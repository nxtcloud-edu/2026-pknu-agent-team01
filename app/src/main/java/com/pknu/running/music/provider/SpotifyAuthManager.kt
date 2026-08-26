package com.pknu.running.music.provider

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify OAuth 2.0 (Authorization Code with PKCE) 인증 관리자.
 *
 * 모바일 앱이므로 Client Secret 없이 PKCE를 사용한다.
 * 토큰은 EncryptedSharedPreferences에 안전하게 저장한다.
 */
class SpotifyAuthManager(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) {
    companion object {
        private const val TAG = "SpotifyAuth"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"

        private const val PREFS_NAME = "spotify_auth_prefs"
        private const val PKCE_PREFS_NAME = "spotify_pkce_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_CODE_VERIFIER = "code_verifier"

        /**
         * code_verifier를 static으로 보관.
         * 같은 프로세스 내에서 DemoActivity → SpotifyAuthCallbackActivity 간에 공유.
         */
        @Volatile
        var pendingCodeVerifier: String? = null
            private set

        fun setPendingCodeVerifier(verifier: String) {
            pendingCodeVerifier = verifier
        }

        // 필요한 Spotify 권한 스코프
        private val SCOPES = listOf(
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "playlist-read-private",
            "playlist-read-collaborative",
            "streaming"
        )
    }

    private val httpClient = OkHttpClient()
    private val prefs: SharedPreferences by lazy { createEncryptedPrefs() }
    private val pkcePrefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PKCE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Public API ---

    fun isAuthenticated(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        return token != null && System.currentTimeMillis() < expiresAt
    }

    suspend fun getAccessToken(): String? {
        val currentToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)

        if (currentToken != null && System.currentTimeMillis() < expiresAt - 300_000) {
            return currentToken
        }

        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken != null) {
            val newToken = refreshAccessToken(refreshToken)
            if (newToken != null) return newToken
        }

        return null
    }

    fun startAuthFlow(context: Context) {
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        // 1) static 변수에 보관 (같은 프로세스 내 즉시 접근)
        setPendingCodeVerifier(codeVerifier)

        // 2) SharedPreferences에도 백업 (프로세스 kill 대비)
        pkcePrefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).commit()

        Log.d(TAG, "code_verifier set (static + prefs), length=${codeVerifier.length}")

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()

        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, authUri)
    }

    suspend fun handleAuthCallback(code: String): Boolean {
        // static에서 먼저 가져오고, 없으면 SharedPreferences 폴백
        val codeVerifier = pendingCodeVerifier
            ?: pkcePrefs.getString(KEY_CODE_VERIFIER, null)

        Log.d(TAG, "handleAuthCallback: code=${code.take(10)}..., verifier=${if (codeVerifier != null) "found(${codeVerifier.length})" else "NULL"}, source=${if (pendingCodeVerifier != null) "static" else "prefs"}")

        if (codeVerifier == null) {
            Log.e(TAG, "code_verifier not found anywhere!")
            return false
        }

        val formBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", clientId)
            .add("code_verifier", codeVerifier)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(body)
                val tokenResponse = SpotifyTokenResponse.fromJson(json)
                saveTokens(tokenResponse)
                // 클리어
                pendingCodeVerifier = null
                pkcePrefs.edit().remove(KEY_CODE_VERIFIER).apply()
                Log.d(TAG, "Token exchange successful!")
                true
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code} $body")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error", e)
            false
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        pkcePrefs.edit().clear().apply()
        pendingCodeVerifier = null
    }

    // --- Internal ---

    private fun refreshAccessToken(refreshToken: String): String? {
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body!!.string())
                val tokenResponse = SpotifyTokenResponse.fromJson(json)
                saveTokens(tokenResponse)
                Log.d(TAG, "Token refresh successful")
                tokenResponse.accessToken
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            null
        }
    }

    private fun saveTokens(tokenResponse: SpotifyTokenResponse) {
        val expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            if (tokenResponse.refreshToken != null) {
                putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
            }
            putLong(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
