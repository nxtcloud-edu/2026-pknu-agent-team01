package com.pknu.running.music.provider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Spotify Web API HTTP 클라이언트.
 * 인증된 access token을 사용하여 REST 호출을 수행한다.
 */
class SpotifyApi(
    private val authManager: SpotifyAuthManager
) {
    companion object {
        private const val TAG = "SpotifyApi"
        private const val BASE_URL = "https://api.spotify.com/v1"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // --- 플레이리스트 ---

    /**
     * 현재 사용자의 플레이리스트 목록을 조회한다.
     * GET /me/playlists
     */
    suspend fun getCurrentUserPlaylists(limit: Int = 50, offset: Int = 0): List<SpotifyPlaylistItem>? {
        val json = get("/me/playlists?limit=$limit&offset=$offset") ?: return null
        return SpotifyPlaylistItem.listFromJson(json)
    }

    /**
     * 특정 플레이리스트의 트랙 목록을 조회한다.
     * GET /playlists/{playlistId}/tracks
     */
    suspend fun getPlaylistTracks(playlistId: String, limit: Int = 100, offset: Int = 0): List<SpotifyTrackItem>? {
        val json = get("/playlists/$playlistId/tracks?limit=$limit&offset=$offset&fields=items(track(id,name,artists,duration_ms,album(images),uri))") ?: return null
        return SpotifyTrackItem.listFromPlaylistJson(json)
    }

    // --- 트랙 메타데이터 ---

    /**
     * 트랙의 오디오 특성(BPM, 에너지 등)을 조회한다.
     * GET /audio-features/{trackId}
     */
    suspend fun getAudioFeatures(trackId: String): SpotifyAudioFeatures? {
        val json = get("/audio-features/$trackId") ?: return null
        return try {
            SpotifyAudioFeatures.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse audio features", e)
            null
        }
    }

    /**
     * 여러 트랙의 오디오 특성을 한 번에 조회한다.
     * GET /audio-features?ids=...
     */
    suspend fun getAudioFeaturesForTracks(trackIds: List<String>): List<SpotifyAudioFeatures>? {
        if (trackIds.isEmpty()) return emptyList()
        // API 제한: 최대 100개
        val ids = trackIds.take(100).joinToString(",")
        val json = get("/audio-features?ids=$ids") ?: return null
        return SpotifyAudioFeatures.listFromJson(json)
    }

    /**
     * 아티스트 정보(장르 포함)를 조회한다.
     * GET /artists/{artistId}
     */
    suspend fun getArtist(artistId: String): SpotifyArtist? {
        val json = get("/artists/$artistId") ?: return null
        return try {
            SpotifyArtist.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse artist", e)
            null
        }
    }

    // --- 재생 제어 ---

    /**
     * 특정 트랙을 재생한다.
     * PUT /me/player/play
     */
    suspend fun play(trackUri: String, positionMs: Long = 0, deviceId: String? = null): Boolean {
        val body = JSONObject().apply {
            put("uris", org.json.JSONArray().put(trackUri))
            put("position_ms", positionMs)
        }
        val url = if (deviceId != null) "/me/player/play?device_id=$deviceId" else "/me/player/play"
        return put(url, body.toString()) != null
    }

    /**
     * 재생을 일시정지한다.
     * PUT /me/player/pause
     */
    suspend fun pause(deviceId: String? = null): Boolean {
        val url = if (deviceId != null) "/me/player/pause?device_id=$deviceId" else "/me/player/pause"
        return put(url, null) != null
    }

    /**
     * 재생을 재개한다.
     * PUT /me/player/play (body 없이)
     */
    suspend fun resume(deviceId: String? = null): Boolean {
        val url = if (deviceId != null) "/me/player/play?device_id=$deviceId" else "/me/player/play"
        return put(url, null) != null
    }

    /**
     * 볼륨을 설정한다.
     * PUT /me/player/volume?volume_percent={0~100}
     */
    suspend fun setVolume(volumePercent: Int, deviceId: String? = null): Boolean {
        val percent = volumePercent.coerceIn(0, 100)
        val url = buildString {
            append("/me/player/volume?volume_percent=$percent")
            if (deviceId != null) append("&device_id=$deviceId")
        }
        return put(url, null) != null
    }

    /**
     * 현재 재생 상태를 조회한다.
     * GET /me/player
     */
    suspend fun getPlaybackState(): SpotifyPlaybackState? {
        val json = get("/me/player") ?: return null
        return try {
            SpotifyPlaybackState.fromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse playback state", e)
            null
        }
    }

    /**
     * 사용 가능한 디바이스 목록을 조회한다.
     * GET /me/player/devices
     */
    suspend fun getAvailableDevices(): JSONObject? {
        return get("/me/player/devices")
    }

    // --- HTTP Helpers ---

    private suspend fun get(path: String): JSONObject? = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: run {
            Log.e(TAG, "No access token available")
            return@withContext null
        }

        val request = Request.Builder()
            .url("$BASE_URL$path")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful -> {
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) null else JSONObject(body)
                }
                response.code == 204 -> null  // No Content (정상이지만 body 없음)
                response.code == 401 -> {
                    Log.w(TAG, "Unauthorized - token may be expired")
                    null
                }
                else -> {
                    Log.e(TAG, "GET $path failed: ${response.code} ${response.body?.string()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GET $path error", e)
            null
        }
    }

    private suspend fun put(path: String, jsonBody: String?): JSONObject? = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: run {
            Log.e(TAG, "No access token available")
            return@withContext null
        }

        val body = jsonBody?.toRequestBody("application/json".toMediaType())
            ?: "".toRequestBody(null)

        val request = Request.Builder()
            .url("$BASE_URL$path")
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            when {
                response.isSuccessful || response.code == 204 -> {
                    JSONObject().put("success", true)
                }
                response.code == 401 -> {
                    Log.w(TAG, "Unauthorized - token may be expired")
                    null
                }
                else -> {
                    Log.e(TAG, "PUT $path failed: ${response.code} ${response.body?.string()}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PUT $path error", e)
            null
        }
    }
}
