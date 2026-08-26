package com.pknu.running.music.provider

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pknu.running.music.MusicProvider
import com.pknu.running.music.model.Playlist
import com.pknu.running.music.model.Track
import com.pknu.running.music.model.TrackMetadata
import kotlinx.coroutines.*

/**
 * Spotify에서 플레이리스트/메타데이터를 조회하고,
 * YouTube에서 오디오를 추출하여 ExoPlayer로 재생하는 하이브리드 어댑터.
 *
 * 흐름:
 *   1. Spotify OAuth 인증 (Free 계정 OK)
 *   2. 사용자 플레이리스트 & 트랙 정보 조회 (Spotify Web API)
 *   3. 재생 시 → "아티스트 - 곡명"으로 YouTube 검색 → 오디오 URL 추출
 *   4. ExoPlayer로 오디오 스트림 재생
 *
 * Spotify Premium 불필요.
 */
class YouTubePlayerAdapter(
    private val context: Context,
    private val spotifyAdapter: SpotifyAdapter
) : MusicProvider {

    companion object {
        private const val TAG = "YouTubePlayerAdapter"
    }

    private val resolver = YouTubeStreamResolver()
    private var exoPlayer: ExoPlayer? = null
    private var volume: Float = 1.0f
    private var currentTrackId: String? = null
    private var onTrackEndListener: (() -> Unit)? = null
    private var durationLimitJob: Job? = null

    // 트랙 정보 캐시 (trackId → Track)
    private val trackCache = mutableMapOf<String, Track>()

    // --- 인증 (Spotify에 위임) ---

    override suspend fun authenticate(): Boolean {
        return spotifyAdapter.authenticate()
    }

    override fun isAuthenticated(): Boolean = spotifyAdapter.isAuthenticated()

    /**
     * Spotify 인증 흐름 시작 (Chrome Custom Tab).
     */
    fun startAuthFlow(activityContext: Context) {
        spotifyAdapter.startAuthFlow(activityContext)
    }

    // --- 플레이리스트 (Spotify에서 조회) ---

    override suspend fun getPlaylists(): List<Playlist> {
        return spotifyAdapter.getPlaylists()
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        val tracks = spotifyAdapter.getPlaylistTracks(playlistId)
        // 캐시에 저장
        tracks.forEach { trackCache[it.id] = it }
        return tracks
    }

    // --- 재생 제어 (YouTube + ExoPlayer) ---

    override suspend fun playTrack(trackId: String, startPositionMs: Long, durationMs: Long?) {
        val track = trackCache[trackId]
        if (track == null) {
            Log.e(TAG, "Track not found in cache: $trackId")
            return
        }

        // YouTube에서 오디오 URL 추출
        val query = "${track.artist} - ${track.title}"
        Log.d(TAG, "Searching YouTube for: $query")

        val resolved = resolver.resolveAudioUrl(query)
        if (resolved == null) {
            Log.e(TAG, "Failed to resolve YouTube audio for: $query")
            return
        }

        Log.d(TAG, "Playing: ${resolved.title} (${resolved.durationSec}s)")

        // ExoPlayer로 재생
        withContext(Dispatchers.Main) {
            val player = getOrCreatePlayer()
            val mediaItem = MediaItem.fromUri(resolved.audioUrl)
            player.setMediaItem(mediaItem)
            player.prepare()

            if (startPositionMs > 0) {
                player.seekTo(startPositionMs)
            }

            player.playWhenReady = true
            player.volume = volume
            currentTrackId = trackId
        }

        // durationMs 제한이 있으면 해당 시간 후 정지 (하이라이트 재생용)
        if (durationMs != null) {
            durationLimitJob?.cancel()
            durationLimitJob = CoroutineScope(Dispatchers.Main).launch {
                delay(durationMs)
                pause()
                onTrackEndListener?.invoke()
            }
        }
    }

    override suspend fun pause() {
        durationLimitJob?.cancel()
        withContext(Dispatchers.Main) {
            exoPlayer?.playWhenReady = false
        }
    }

    override suspend fun resume() {
        withContext(Dispatchers.Main) {
            exoPlayer?.playWhenReady = true
        }
    }

    override suspend fun stop() {
        durationLimitJob?.cancel()
        currentTrackId = null
        withContext(Dispatchers.Main) {
            exoPlayer?.stop()
        }
    }

    // --- 볼륨 ---

    override suspend fun setVolume(level: Float) {
        volume = level.coerceIn(0.0f, 1.0f)
        withContext(Dispatchers.Main) {
            exoPlayer?.volume = volume
        }
    }

    override fun getVolume(): Float = volume

    // --- 메타데이터 (Spotify에서 조회) ---

    override suspend fun getTrackMetadata(trackId: String): TrackMetadata? {
        return spotifyAdapter.getTrackMetadata(trackId)
    }

    // --- 이벤트 콜백 ---

    override fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    // --- 리소스 해제 ---

    fun release() {
        durationLimitJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    // --- Internal ---

    private fun getOrCreatePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            onTrackEndListener?.invoke()
                        }
                    }
                })
            }
        }
        return exoPlayer!!
    }
}
