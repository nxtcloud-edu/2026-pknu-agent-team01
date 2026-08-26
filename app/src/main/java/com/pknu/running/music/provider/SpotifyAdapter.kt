package com.pknu.running.music.provider

import android.content.Context
import android.util.Log
import com.pknu.running.music.MusicProvider
import com.pknu.running.music.model.Playlist
import com.pknu.running.music.model.Track
import com.pknu.running.music.model.TrackMetadata
import kotlinx.coroutines.*
import java.util.Timer
import java.util.TimerTask

/**
 * Spotify Web API를 사용하는 MusicProvider 구현체.
 *
 * - OAuth 2.0 PKCE 인증
 * - 사용자 플레이리스트 조회
 * - Web API를 통한 재생 제어 (Spotify Premium 필요)
 * - Audio Features API로 BPM/에너지 메타데이터 조회
 *
 * 사용 전 authenticate()를 호출하여 인증을 완료해야 한다.
 * 인증 흐름:
 *   1. authenticate() 호출 → false 반환 (인증 필요)
 *   2. startAuthFlow(activity) 호출 → Chrome Custom Tab으로 로그인
 *   3. redirect callback에서 handleAuthCallback(code) 호출
 *   4. 이후 authenticate() 호출 시 true 반환
 */
class SpotifyAdapter(
    private val context: Context,
    private val clientId: String,
    private val redirectUri: String
) : MusicProvider {

    companion object {
        private const val TAG = "SpotifyAdapter"
        private const val TRACK_END_POLL_INTERVAL_MS = 2000L
    }

    val authManager = SpotifyAuthManager(context, clientId, redirectUri)
    private val api = SpotifyApi(authManager)

    private var volume: Float = 1.0f
    private var currentTrackId: String? = null
    private var currentTrackDurationMs: Long = 0
    private var onTrackEndListener: (() -> Unit)? = null
    private var trackEndTimer: Timer? = null
    private var isPlaying = false

    // Spotify URI 캐시 (trackId → spotify:track:xxx)
    private val trackUriCache = mutableMapOf<String, String>()

    // --- 인증 ---

    /**
     * 이미 유효한 토큰이 있으면 true.
     * 없으면 false — 이 경우 startAuthFlow()를 호출해야 한다.
     */
    override suspend fun authenticate(): Boolean {
        return authManager.isAuthenticated()
    }

    override fun isAuthenticated(): Boolean = authManager.isAuthenticated()

    /**
     * Chrome Custom Tab으로 Spotify 로그인 화면을 연다.
     * Activity context를 전달해야 한다.
     */
    fun startAuthFlow(activityContext: Context) {
        authManager.startAuthFlow(activityContext)
    }

    /**
     * Redirect URI callback에서 authorization code를 처리한다.
     */
    suspend fun handleAuthCallback(code: String): Boolean {
        return authManager.handleAuthCallback(code)
    }

    // --- 플레이리스트 ---

    override suspend fun getPlaylists(): List<Playlist> {
        val spotifyPlaylists = api.getCurrentUserPlaylists() ?: return emptyList()
        return spotifyPlaylists.map { sp ->
            Playlist(
                id = sp.id,
                name = sp.name,
                trackCount = sp.trackCount,
                imageUrl = sp.imageUrl
            )
        }
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        val spotifyTracks = api.getPlaylistTracks(playlistId) ?: return emptyList()

        // URI 캐시 업데이트
        spotifyTracks.forEach { trackUriCache[it.id] = it.uri }

        return spotifyTracks.map { st ->
            Track(
                id = st.id,
                title = st.name,
                artist = st.artists.joinToString(", "),
                durationMs = st.durationMs,
                albumArt = st.albumImageUrl
            )
        }
    }

    // --- 재생 제어 ---

    override suspend fun playTrack(trackId: String, startPositionMs: Long, durationMs: Long?) {
        val trackUri = trackUriCache[trackId] ?: "spotify:track:$trackId"

        val success = api.play(trackUri, startPositionMs)
        if (success) {
            currentTrackId = trackId
            isPlaying = true

            // durationMs가 지정됐으면 해당 시간 후 자동 정지 (하이라이트 재생용)
            if (durationMs != null) {
                startTrackEndTimer(durationMs)
            } else {
                // 곡 전체 재생 시: 주기적으로 재생 상태를 폴링하여 곡 끝 감지
                startPlaybackPolling()
            }
            Log.d(TAG, "Playing track: $trackId from ${startPositionMs}ms")
        } else {
            Log.e(TAG, "Failed to play track: $trackId")
        }
    }

    override suspend fun pause() {
        stopTimers()
        val success = api.pause()
        if (success) {
            isPlaying = false
            Log.d(TAG, "Paused")
        }
    }

    override suspend fun resume() {
        val success = api.resume()
        if (success) {
            isPlaying = true
            startPlaybackPolling()
            Log.d(TAG, "Resumed")
        }
    }

    override suspend fun stop() {
        stopTimers()
        api.pause() // Spotify Web API에는 stop이 없으므로 pause 사용
        isPlaying = false
        currentTrackId = null
        Log.d(TAG, "Stopped")
    }

    // --- 볼륨 ---

    override suspend fun setVolume(level: Float) {
        val newVolume = level.coerceIn(0.0f, 1.0f)
        val percent = (newVolume * 100).toInt()
        val success = api.setVolume(percent)
        if (success) {
            volume = newVolume
        }
    }

    override fun getVolume(): Float = volume

    // --- 메타데이터 ---

    override suspend fun getTrackMetadata(trackId: String): TrackMetadata? {
        val audioFeatures = api.getAudioFeatures(trackId) ?: return null

        // 장르는 아티스트 API에서 가져와야 하지만, 여기서는 audio features 중심으로 구성
        // 추후 아티스트 장르 조회 추가 가능
        return TrackMetadata(
            trackId = trackId,
            bpm = audioFeatures.bpm.toInt(),
            genre = null, // 아티스트 API 별도 호출 필요 시 확장
            energy = audioFeatures.energy,
            valence = audioFeatures.valence
        )
    }

    // --- 이벤트 콜백 ---

    override fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    // --- 내부: 곡 종료 감지 ---

    private fun startTrackEndTimer(durationMs: Long) {
        stopTimers()
        trackEndTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    CoroutineScope(Dispatchers.Main).launch {
                        pause()
                        onTrackEndListener?.invoke()
                    }
                }
            }, durationMs)
        }
    }

    private fun startPlaybackPolling() {
        stopTimers()
        trackEndTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    CoroutineScope(Dispatchers.IO).launch {
                        val state = api.getPlaybackState()
                        if (state != null && !state.isPlaying && isPlaying) {
                            // Spotify에서 재생이 멈춤 → 곡이 끝난 것으로 판단
                            isPlaying = false
                            withContext(Dispatchers.Main) {
                                onTrackEndListener?.invoke()
                            }
                            stopTimers()
                        }
                    }
                }
            }, TRACK_END_POLL_INTERVAL_MS, TRACK_END_POLL_INTERVAL_MS)
        }
    }

    private fun stopTimers() {
        trackEndTimer?.cancel()
        trackEndTimer = null
    }
}
