package music.provider

import music.MusicProvider
import music.model.Playlist
import music.model.Track
import music.model.TrackMetadata
import java.util.Timer
import java.util.TimerTask

/**
 * 테스트/데모용 가짜 음악 Provider.
 * 실제 음악을 재생하지 않고 타이머로 곡 종료를 시뮬레이션한다.
 *
 * @param defaultTrackDurationMs 기본 곡 재생 시간 (테스트에서 짧게 설정 가능)
 */
class MockMusicAdapter(
    private val defaultTrackDurationMs: Long = 10_000
) : MusicProvider {

    private var authenticated = false
    private var volume: Float = 1.0f
    private var isPlaying = false
    private var currentTrackId: String? = null
    private var onTrackEndListener: (() -> Unit)? = null
    private var playbackTimer: Timer? = null

    // --- Mock 데이터 ---

    private val mockPlaylists = listOf(
        Playlist(id = "pl1", name = "러닝 플레이리스트", trackCount = 5),
        Playlist(id = "pl2", name = "칠 뮤직", trackCount = 3)
    )

    private val mockTracks = mapOf(
        "pl1" to listOf(
            Track(id = "m1", title = "달려라", artist = "Runner", durationMs = 180_000),
            Track(id = "m2", title = "Love Song", artist = "Heart", durationMs = 240_000),
            Track(id = "m3", title = "Sprint!", artist = "Fast", durationMs = 150_000),
            Track(id = "m4", title = "Chill Wave", artist = "Slow", durationMs = 200_000),
            Track(id = "m5", title = "Final Boss", artist = "Epic", durationMs = 210_000)
        ),
        "pl2" to listOf(
            Track(id = "m6", title = "Sunset", artist = "Calm", durationMs = 220_000),
            Track(id = "m7", title = "Ocean", artist = "Wave", durationMs = 190_000),
            Track(id = "m8", title = "Dream", artist = "Sleep", durationMs = 250_000)
        )
    )

    private val mockMetadata = mapOf(
        "m1" to TrackMetadata(trackId = "m1", bpm = 140, genre = "pop", energy = 0.8f),
        "m2" to TrackMetadata(trackId = "m2", bpm = 85, genre = "ballad", energy = 0.3f),
        "m3" to TrackMetadata(trackId = "m3", bpm = 160, genre = "electronic", energy = 0.9f),
        "m4" to TrackMetadata(trackId = "m4", bpm = 95, genre = "ambient", energy = 0.2f),
        "m5" to TrackMetadata(trackId = "m5", bpm = 135, genre = "cinematic", energy = 0.85f),
        "m6" to TrackMetadata(trackId = "m6", bpm = 90, genre = "ambient", energy = 0.2f),
        "m7" to TrackMetadata(trackId = "m7", bpm = 80, genre = "ambient", energy = 0.15f),
        "m8" to TrackMetadata(trackId = "m8", bpm = 70, genre = "ambient", energy = 0.1f)
    )

    // --- 인증 ---

    override suspend fun authenticate(): Boolean {
        authenticated = true
        return true
    }

    override fun isAuthenticated(): Boolean = authenticated

    // --- 플레이리스트 ---

    override suspend fun getPlaylists(): List<Playlist> {
        return mockPlaylists
    }

    override suspend fun getPlaylistTracks(playlistId: String): List<Track> {
        return mockTracks[playlistId] ?: emptyList()
    }

    // --- 재생 제어 ---

    override suspend fun playTrack(trackId: String, startPositionMs: Long, durationMs: Long?) {
        stopTimer()
        currentTrackId = trackId
        isPlaying = true

        val playDuration = durationMs ?: defaultTrackDurationMs
        startTimer(playDuration)
    }

    override suspend fun pause() {
        stopTimer()
        isPlaying = false
    }

    override suspend fun resume() {
        if (currentTrackId != null) {
            isPlaying = true
            // 간단히 남은 시간 대신 기본 시간으로 재시작
            startTimer(defaultTrackDurationMs)
        }
    }

    override suspend fun stop() {
        stopTimer()
        isPlaying = false
        currentTrackId = null
    }

    // --- 볼륨 ---

    override suspend fun setVolume(level: Float) {
        volume = level.coerceIn(0.0f, 1.0f)
    }

    override fun getVolume(): Float = volume

    // --- 메타데이터 ---

    override suspend fun getTrackMetadata(trackId: String): TrackMetadata? {
        return mockMetadata[trackId]
    }

    // --- 이벤트 콜백 ---

    override fun setOnTrackEndListener(listener: () -> Unit) {
        onTrackEndListener = listener
    }

    // --- 상태 조회 (테스트용) ---

    fun isCurrentlyPlaying(): Boolean = isPlaying

    fun getCurrentTrackId(): String? = currentTrackId

    // --- 내부 ---

    private fun startTimer(durationMs: Long) {
        playbackTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    isPlaying = false
                    onTrackEndListener?.invoke()
                }
            }, durationMs)
        }
    }

    private fun stopTimer() {
        playbackTimer?.cancel()
        playbackTimer = null
    }
}
