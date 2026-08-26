package music

import music.model.*

/**
 * 음악 모듈의 메인 진입점.
 * 플레이리스트 로드, 재생 제어, 이벤트 기반 음악 전환, Audio Ducking을 관리한다.
 *
 * 이벤트 처리 방식:
 * - 곡이 끝나는 시점에 랜덤으로 이벤트 발생 여부를 판정한다.
 * - 이벤트 발생 시 해당 테마 곡의 하이라이트 30초를 재생한다.
 * - 30초 끝나면 원래 플레이리스트 다음 곡으로 복귀한다.
 */
class MusicController(
    private val provider: MusicProvider,
    private val tagger: TrackTagger = TrackTagger(),
    private val selector: TrackSelector = TrackSelector(),
    private val ducker: AudioDucker = AudioDucker(provider),
    private val eventProbability: Float = 0.3f,
    private val highlightDurationMs: Long = 30_000
) {

    // --- 상태 ---

    private var playlist: List<TaggedTrack> = emptyList()
    private var eventTracks: List<TaggedTrack> = emptyList()
    private var playHistory: MutableList<String> = mutableListOf()
    private var currentIndex: Int = -1
    private var playMode: PlayMode = PlayMode.SEQUENTIAL
    private var activeMusicRule: MusicRule = MusicRule.DEFAULT
    private var playbackState: PlaybackState = PlaybackState.IDLE
    private var currentTrack: TaggedTrack? = null
    private var isPlayingEventTrack: Boolean = false
    private var playLogs: MutableList<TrackPlayLog> = mutableListOf()
    private var currentTrackStartedAt: Long = 0

    // --- 공개 API ---

    fun getPlaybackState(): PlaybackState = playbackState

    fun getCurrentTrack(): TaggedTrack? = currentTrack

    fun getPlayMode(): PlayMode = playMode

    fun getActiveMusicRule(): MusicRule = activeMusicRule

    fun isPlayingEventTrack(): Boolean = isPlayingEventTrack

    fun getPlayLogs(): List<TrackPlayLog> = playLogs.toList()

    /**
     * 플레이리스트를 로드하고 태깅한다.
     */
    suspend fun loadPlaylist(playlistId: String) {
        playbackState = PlaybackState.LOADING

        val tracks = provider.getPlaylistTracks(playlistId)
        val metadataMap = tracks.associate { track ->
            track.id to provider.getTrackMetadata(track.id)
        }
        playlist = tagger.tagPlaylist(tracks, metadataMap)

        playHistory.clear()
        currentIndex = -1
        playbackState = PlaybackState.IDLE
    }

    /**
     * 이벤트 발생 시 재생할 테마곡들을 등록한다.
     * MusicTag별로 분류되어 이벤트 타입에 맞는 곡이 선택된다.
     */
    suspend fun loadEventTracks(tracks: List<Track>) {
        val metadataMap = tracks.associate { track ->
            track.id to provider.getTrackMetadata(track.id)
        }
        eventTracks = tagger.tagPlaylist(tracks, metadataMap)
    }

    /**
     * 재생을 시작한다. 플레이리스트가 비어있으면 무시한다.
     */
    suspend fun play() {
        if (playlist.isEmpty()) return

        val next = selector.selectNext(playlist, activeMusicRule, playHistory, currentIndex, playMode)
            ?: return

        playTrack(next)
    }

    /**
     * 일시정지한다.
     */
    suspend fun pause() {
        if (playbackState != PlaybackState.PLAYING) return
        provider.pause()
        playbackState = PlaybackState.PAUSED
    }

    /**
     * 재개한다.
     */
    suspend fun resume() {
        if (playbackState != PlaybackState.PAUSED) return
        provider.resume()
        playbackState = PlaybackState.PLAYING
    }

    /**
     * 정지한다. (세션 종료)
     */
    suspend fun stop() {
        logCurrentTrack()
        provider.stop()
        playbackState = PlaybackState.IDLE
        currentTrack = null
        isPlayingEventTrack = false
    }

    /**
     * 다음 곡으로 넘긴다.
     */
    suspend fun next() {
        handleTrackEnd()
    }

    /**
     * 재생 모드를 변경한다.
     */
    fun setPlayMode(mode: PlayMode) {
        playMode = mode
    }

    /**
     * 음악 규칙을 변경한다. (외부 이벤트/페이스 변화 시 호출)
     * 현재 곡은 유지하고, 다음 곡부터 적용된다.
     */
    fun setMusicRule(rule: MusicRule) {
        activeMusicRule = rule
    }

    /**
     * 음악 규칙을 기본으로 초기화한다.
     */
    fun clearMusicRule() {
        activeMusicRule = MusicRule.DEFAULT
    }

    /**
     * TTS 재생 시 볼륨을 낮춘다.
     */
    suspend fun duckVolume() {
        ducker.duck()
    }

    /**
     * TTS 종료 후 볼륨을 복구한다.
     */
    suspend fun restoreVolume() {
        ducker.restore()
    }

    /**
     * 초기화 — onTrackEnd 리스너를 등록한다. 반드시 play 전에 호출할 것.
     */
    fun initialize() {
        provider.setOnTrackEndListener {
            // 코루틴 컨텍스트에서 호출되어야 하므로, 외부에서 처리 필요
            // 여기서는 콜백만 저장하고 외부에서 handleTrackEnd를 호출하게 함
            onTrackEndCallback?.invoke()
        }
    }

    private var onTrackEndCallback: (() -> Unit)? = null

    /**
     * 외부에서 곡 종료 이벤트를 처리하기 위한 콜백을 등록한다.
     */
    fun setOnTrackEndHandler(handler: () -> Unit) {
        onTrackEndCallback = handler
    }

    /**
     * 곡이 끝났을 때 호출한다.
     * 랜덤 이벤트 판정 → 이벤트곡 30초 or 다음 곡 재생.
     */
    suspend fun handleTrackEnd() {
        logCurrentTrack()

        if (isPlayingEventTrack) {
            // 이벤트곡 30초 끝남 → 원래 플레이리스트 복귀
            isPlayingEventTrack = false
            playNextFromPlaylist()
            return
        }

        // 랜덤 이벤트 판정
        if (shouldTriggerEvent()) {
            playEventTrack()
        } else {
            playNextFromPlaylist()
        }
    }

    // --- 내부 로직 ---

    private suspend fun playTrack(taggedTrack: TaggedTrack) {
        currentTrack = taggedTrack
        currentIndex = playlist.indexOf(taggedTrack)
        currentTrackStartedAt = System.currentTimeMillis()
        playHistory.add(taggedTrack.track.id)
        playbackState = PlaybackState.PLAYING

        provider.playTrack(taggedTrack.track.id)
    }

    private suspend fun playNextFromPlaylist() {
        val next = selector.selectNext(playlist, activeMusicRule, playHistory, currentIndex, playMode)
        if (next != null) {
            playTrack(next)
        } else {
            // 플레이리스트 소진 — 처음부터
            playHistory.clear()
            currentIndex = -1
            play()
        }
    }

    private suspend fun playEventTrack() {
        val eventRule = activeMusicRule
        val eventTrack = selector.selectNext(eventTracks, eventRule, emptyList(), -1, PlayMode.SHUFFLE)

        if (eventTrack == null) {
            // 이벤트곡 없으면 그냥 다음 곡
            playNextFromPlaylist()
            return
        }

        isPlayingEventTrack = true
        currentTrack = eventTrack
        currentTrackStartedAt = System.currentTimeMillis()
        playbackState = PlaybackState.PLAYING

        // 하이라이트 구간: 곡 중간(30%)부터 30초 재생
        val startPosition = (eventTrack.track.durationMs * 0.3).toLong()
        provider.playTrack(
            trackId = eventTrack.track.id,
            startPositionMs = startPosition,
            durationMs = highlightDurationMs
        )
    }

    private fun shouldTriggerEvent(): Boolean {
        if (eventTracks.isEmpty()) return false
        return Math.random() < eventProbability
    }

    private fun logCurrentTrack() {
        val track = currentTrack ?: return
        val now = System.currentTimeMillis()
        playLogs.add(
            TrackPlayLog(
                trackId = track.track.id,
                startedAt = currentTrackStartedAt,
                endedAt = now,
                musicRule = activeMusicRule,
                wasEventTrack = isPlayingEventTrack
            )
        )
    }
}
