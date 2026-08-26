package com.pknu.running.music

import com.pknu.running.music.model.Playlist
import com.pknu.running.music.model.Track
import com.pknu.running.music.model.TrackMetadata

/**
 * 외부 음악 서비스와의 통신을 추상화하는 인터페이스.
 * 구현체: SpotifyAdapter, MockMusicAdapter
 */
interface MusicProvider {

    // --- 인증 ---

    suspend fun authenticate(): Boolean

    fun isAuthenticated(): Boolean

    // --- 플레이리스트 ---

    suspend fun getPlaylists(): List<Playlist>

    suspend fun getPlaylistTracks(playlistId: String): List<Track>

    // --- 재생 제어 ---

    /**
     * 트랙을 재생한다.
     * @param startPositionMs 재생 시작 위치 (하이라이트 30초 재생용)
     * @param durationMs 재생할 길이. null이면 끝까지 재생.
     */
    suspend fun playTrack(
        trackId: String,
        startPositionMs: Long = 0,
        durationMs: Long? = null
    )

    suspend fun pause()

    suspend fun resume()

    suspend fun stop()

    // --- 볼륨 ---

    suspend fun setVolume(level: Float)  // 0.0 ~ 1.0

    fun getVolume(): Float

    // --- 메타데이터 ---

    suspend fun getTrackMetadata(trackId: String): TrackMetadata?

    // --- 이벤트 콜백 ---

    fun setOnTrackEndListener(listener: () -> Unit)
}
