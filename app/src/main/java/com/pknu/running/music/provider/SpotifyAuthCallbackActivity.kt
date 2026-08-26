package com.pknu.running.music.provider

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Spotify OAuth redirect를 수신하는 Activity.
 * deep link scheme: runningapp://callback?code=...
 *
 * 이 Activity는 화면 없이 redirect만 처리하고 DemoActivity로 돌아간다.
 */
class SpotifyAuthCallbackActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SpotifyAuthCallback"

        /** 인증 결과를 수신하기 위한 콜백. */
        var onAuthResult: ((success: Boolean) -> Unit)? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            Log.e(TAG, "No URI in intent")
            notifyAndFinish(false)
            return
        }

        Log.d(TAG, "Received URI: $uri")

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        when {
            error != null -> {
                Log.e(TAG, "Auth error: $error")
                notifyAndFinish(false)
            }
            code != null -> {
                Log.d(TAG, "Authorization code received (${code.length} chars)")
                val clientId = com.pknu.running.BuildConfig.SPOTIFY_CLIENT_ID
                val redirectUri = com.pknu.running.BuildConfig.SPOTIFY_REDIRECT_URI

                // applicationContext를 사용하여 동일한 SharedPreferences에 접근
                val authManager = SpotifyAuthManager(applicationContext, clientId, redirectUri)

                CoroutineScope(Dispatchers.IO).launch {
                    val success = authManager.handleAuthCallback(code)
                    Log.d(TAG, "Token exchange result: $success")
                    runOnUiThread { notifyAndFinish(success) }
                }
            }
            else -> {
                Log.e(TAG, "No code or error in URI: $uri")
                notifyAndFinish(false)
            }
        }
    }

    private fun notifyAndFinish(success: Boolean) {
        onAuthResult?.invoke(success)
        // DemoActivity로 명시적 복귀
        try {
            val intent = Intent(this, Class.forName("com.pknu.running.demo.DemoActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate back", e)
        }
        finish()
    }
}
