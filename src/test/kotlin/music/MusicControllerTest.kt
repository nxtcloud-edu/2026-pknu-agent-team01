package music

import kotlinx.coroutines.runBlocking
import music.model.*
import music.provider.MockMusicAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MusicControllerTest {

    private lateinit var provider: MockMusicAdapter
    private lateinit var controller: MusicController

    @BeforeEach
    fun setUp() {
        provider = MockMusicAdapter(defaultTrackDurationMs = 5000)
        controller = MusicController(
            provider = provider,
            eventProbability = 0.0f,  // 기본: 이벤트 발생 안 함
            highlightDurationMs = 100
        )
    }

    private fun controllerWithEvents(probability: Float = 1.0f): MusicController {
        return MusicController(
            provider = provider,
            eventProbability = probability,
            highlightDurationMs = 100
        )
    }

    @Test
    fun `초기 상태는 IDLE이다`() {
        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
        assertNull(controller.getCurrentTrack())
    }

    @Test
    fun `loadPlaylist 후 상태는 IDLE이다`() = runBlocking {
        controller.loadPlaylist("pl1")
        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
    }

    @Test
    fun `play 후 상태는 PLAYING이다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()

        assertEquals(PlaybackState.PLAYING, controller.getPlaybackState())
        assertNotNull(controller.getCurrentTrack())
    }

    @Test
    fun `빈 플레이리스트에서 play는 무시된다`() = runBlocking {
        controller.loadPlaylist("unknown")
        controller.play()

        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
    }

    @Test
    fun `pause 후 상태는 PAUSED이다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()
        controller.pause()

        assertEquals(PlaybackState.PAUSED, controller.getPlaybackState())
    }

    @Test
    fun `resume 후 상태는 PLAYING이다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()
        controller.pause()
        controller.resume()

        assertEquals(PlaybackState.PLAYING, controller.getPlaybackState())
    }

    @Test
    fun `stop 후 상태는 IDLE이고 현재 트랙은 null이다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()
        controller.stop()

        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
        assertNull(controller.getCurrentTrack())
    }

    @Test
    fun `handleTrackEnd 호출 시 다음 곡이 재생된다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()

        val firstTrack = controller.getCurrentTrack()
        controller.handleTrackEnd()
        val secondTrack = controller.getCurrentTrack()

        assertNotNull(secondTrack)
        assertNotEquals(firstTrack?.track?.id, secondTrack?.track?.id)
    }

    @Test
    fun `setPlayMode SHUFFLE로 변경된다`() = runBlocking {
        controller.setPlayMode(PlayMode.SHUFFLE)
        assertEquals(PlayMode.SHUFFLE, controller.getPlayMode())
    }

    @Test
    fun `setMusicRule로 규칙이 변경된다`() {
        val rule = MusicRule(
            preferredTags = listOf(MusicTag.HIGH_ENERGY),
            source = "test"
        )
        controller.setMusicRule(rule)

        assertEquals(rule, controller.getActiveMusicRule())
    }

    @Test
    fun `clearMusicRule로 기본 규칙으로 복귀한다`() {
        controller.setMusicRule(
            MusicRule(preferredTags = listOf(MusicTag.LOVE), source = "event")
        )
        controller.clearMusicRule()

        assertEquals(MusicRule.DEFAULT, controller.getActiveMusicRule())
    }

    @Test
    fun `duckVolume 호출 시 볼륨이 낮아진다`() = runBlocking {
        provider.setVolume(1.0f)
        controller.loadPlaylist("pl1")
        controller.play()

        controller.duckVolume()

        assertEquals(0.3f, provider.getVolume(), 0.01f)
    }

    @Test
    fun `restoreVolume 호출 시 볼륨이 복구된다`() = runBlocking {
        provider.setVolume(1.0f)
        controller.loadPlaylist("pl1")
        controller.play()

        controller.duckVolume()
        controller.restoreVolume()

        assertEquals(1.0f, provider.getVolume(), 0.01f)
    }

    @Test
    fun `이벤트 확률 100%일 때 이벤트곡이 재생된다`() = runBlocking {
        val ctrl = controllerWithEvents(1.0f)
        ctrl.loadPlaylist("pl1")

        // 이벤트 트랙 등록
        val eventTrackList = listOf(
            Track(id = "ev1", title = "Event Song", artist = "E", durationMs = 120_000)
        )
        ctrl.loadEventTracks(eventTrackList)

        ctrl.play()
        ctrl.handleTrackEnd()

        assertTrue(ctrl.isPlayingEventTrack())
        assertEquals("ev1", ctrl.getCurrentTrack()?.track?.id)
    }

    @Test
    fun `이벤트곡 종료 후 원래 플레이리스트로 복귀한다`() = runBlocking {
        val ctrl = controllerWithEvents(1.0f)
        ctrl.loadPlaylist("pl1")

        val eventTrackList = listOf(
            Track(id = "ev1", title = "Event Song", artist = "E", durationMs = 120_000)
        )
        ctrl.loadEventTracks(eventTrackList)

        ctrl.play()
        ctrl.handleTrackEnd()  // → 이벤트곡 재생
        assertTrue(ctrl.isPlayingEventTrack())

        ctrl.handleTrackEnd()  // → 이벤트곡 끝, 복귀
        assertFalse(ctrl.isPlayingEventTrack())
        assertNotEquals("ev1", ctrl.getCurrentTrack()?.track?.id)
    }

    @Test
    fun `이벤트 트랙이 없으면 이벤트 발생하지 않는다`() = runBlocking {
        val ctrl = controllerWithEvents(1.0f)  // 확률 100%지만 이벤트곡 없음
        ctrl.loadPlaylist("pl1")

        ctrl.play()
        ctrl.handleTrackEnd()

        assertFalse(ctrl.isPlayingEventTrack())
    }

    @Test
    fun `stop 후 playLog가 기록된다`() = runBlocking {
        controller.loadPlaylist("pl1")
        controller.play()
        controller.stop()

        val logs = controller.getPlayLogs()
        assertEquals(1, logs.size)
        assertFalse(logs[0].wasEventTrack)
    }
}
