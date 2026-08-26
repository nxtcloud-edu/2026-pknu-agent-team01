package com.pknu.running.music

import kotlinx.coroutines.runBlocking
import com.pknu.running.music.model.*
import com.pknu.running.music.provider.MockMusicAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 음악 모듈 통합 테스트.
 * Mock 환경에서 전체 플로우를 시나리오 기반으로 검증한다.
 */
class MusicIntegrationTest {

    private lateinit var provider: MockMusicAdapter
    private lateinit var controller: MusicController

    private val eventTracks = listOf(
        Track(id = "ev1", title = "Love Event", artist = "Heart", durationMs = 120_000),
        Track(id = "ev2", title = "Chase Event", artist = "Speed", durationMs = 100_000),
        Track(id = "ev3", title = "Epic Finale", artist = "Orchestra", durationMs = 150_000)
    )

    @BeforeEach
    fun setUp() {
        provider = MockMusicAdapter(defaultTrackDurationMs = 100)
    }

    // ============================================================
    // 시나리오 1: 기본 재생 플로우
    // ============================================================

    @Test
    fun `시나리오1 - 플레이리스트 로드부터 정지까지 기본 플로우`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        // 1. 플레이리스트 로드
        controller.loadPlaylist("pl1")
        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())

        // 2. 재생 시작
        controller.play()
        assertEquals(PlaybackState.PLAYING, controller.getPlaybackState())
        assertNotNull(controller.getCurrentTrack())
        val firstTrack = controller.getCurrentTrack()!!

        // 3. 곡 종료 → 다음 곡
        controller.handleTrackEnd()
        assertEquals(PlaybackState.PLAYING, controller.getPlaybackState())
        val secondTrack = controller.getCurrentTrack()!!
        assertNotEquals(firstTrack.track.id, secondTrack.track.id)

        // 4. 일시정지
        controller.pause()
        assertEquals(PlaybackState.PAUSED, controller.getPlaybackState())

        // 5. 재개
        controller.resume()
        assertEquals(PlaybackState.PLAYING, controller.getPlaybackState())

        // 6. 정지
        controller.stop()
        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
        assertNull(controller.getCurrentTrack())
    }

    // ============================================================
    // 시나리오 2: 이벤트 발생 → 30초 하이라이트 → 복귀
    // ============================================================

    @Test
    fun `시나리오2 - 이벤트 발생 시 30초 하이라이트 재생 후 원래 플레이리스트 복귀`() = runBlocking {
        controller = MusicController(
            provider = provider,
            eventProbability = 1.0f,  // 항상 이벤트 발생
            highlightDurationMs = 100
        )

        controller.loadPlaylist("pl1")
        controller.loadEventTracks(eventTracks)
        controller.play()

        val playlistTrack = controller.getCurrentTrack()!!
        assertFalse(controller.isPlayingEventTrack())

        // 곡 끝 → 이벤트 발생
        controller.handleTrackEnd()
        assertTrue(controller.isPlayingEventTrack())
        val eventTrack = controller.getCurrentTrack()!!
        assertTrue(eventTrack.track.id.startsWith("ev"))

        // 이벤트곡 30초 끝 → 복귀
        controller.handleTrackEnd()
        assertFalse(controller.isPlayingEventTrack())
        assertFalse(controller.getCurrentTrack()!!.track.id.startsWith("ev"))
    }

    // ============================================================
    // 시나리오 3: 이벤트 미발생 시 일반 다음 곡
    // ============================================================

    @Test
    fun `시나리오3 - 이벤트 확률 0이면 항상 일반 다음 곡`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        controller.loadPlaylist("pl1")
        controller.loadEventTracks(eventTracks)
        controller.play()

        // 5곡 연속 재생 — 이벤트 발생 안 함
        repeat(5) {
            controller.handleTrackEnd()
            assertFalse(controller.isPlayingEventTrack())
        }
    }

    // ============================================================
    // 시나리오 4: 음악 규칙 변경 (페이스/이벤트 기반)
    // ============================================================

    @Test
    fun `시나리오4 - HIGH_ENERGY 규칙 적용 시 해당 태그 곡 우선`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        controller.loadPlaylist("pl1")
        controller.play()

        // 규칙 변경: high-energy 우선
        controller.setMusicRule(
            MusicRule(
                preferredTags = listOf(MusicTag.HIGH_ENERGY),
                source = "pace:fast"
            )
        )

        // 다음 곡들이 high-energy 태그를 가질 확률이 높아짐
        controller.handleTrackEnd()
        val track = controller.getCurrentTrack()!!
        // mock 데이터에서 m1(140bpm), m3(160bpm), m5(135bpm)이 high-energy
        val highEnergyIds = setOf("m1", "m3", "m5")
        assertTrue(
            track.track.id in highEnergyIds,
            "기대: high-energy 곡, 실제: ${track.track.id}"
        )
    }

    @Test
    fun `시나리오4b - 규칙 초기화 후 기본 동작 복귀`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        controller.loadPlaylist("pl1")
        controller.play()

        controller.setMusicRule(
            MusicRule(preferredTags = listOf(MusicTag.LOVE), source = "event")
        )
        controller.clearMusicRule()

        assertEquals(MusicRule.DEFAULT, controller.getActiveMusicRule())
    }

    // ============================================================
    // 시나리오 5: Audio Ducking (TTS 협력)
    // ============================================================

    @Test
    fun `시나리오5 - TTS 시작 시 볼륨 30%, 종료 시 100% 복구`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        provider.setVolume(1.0f)
        controller.loadPlaylist("pl1")
        controller.play()

        // TTS 시작
        controller.duckVolume()
        assertEquals(0.3f, provider.getVolume(), 0.01f)

        // TTS 종료
        controller.restoreVolume()
        assertEquals(1.0f, provider.getVolume(), 0.01f)
    }

    @Test
    fun `시나리오5b - 원래 볼륨이 0_7이면 복구 후 0_7`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        provider.setVolume(0.7f)
        controller.loadPlaylist("pl1")
        controller.play()

        controller.duckVolume()
        assertEquals(0.3f, provider.getVolume(), 0.01f)

        controller.restoreVolume()
        assertEquals(0.7f, provider.getVolume(), 0.01f)
    }

    // ============================================================
    // 시나리오 6: 셔플 모드
    // ============================================================

    @Test
    fun `시나리오6 - 셔플 모드에서 곡 순서가 랜덤이다`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        controller.loadPlaylist("pl1")
        controller.setPlayMode(PlayMode.SHUFFLE)
        assertEquals(PlayMode.SHUFFLE, controller.getPlayMode())

        controller.play()

        // 10번 반복해서 다양한 곡이 나오는지 확인
        val playedIds = mutableSetOf<String>()
        playedIds.add(controller.getCurrentTrack()!!.track.id)
        repeat(10) {
            controller.handleTrackEnd()
            controller.getCurrentTrack()?.let { playedIds.add(it.track.id) }
        }

        // 5곡 중 최소 2곡 이상은 재생되어야 함
        assertTrue(playedIds.size >= 2, "셔플인데 곡 다양성 부족: $playedIds")
    }

    // ============================================================
    // 시나리오 7: PlayLog 기록
    // ============================================================

    @Test
    fun `시나리오7 - 여러 곡 재생 후 PlayLog가 정확히 기록된다`() = runBlocking {
        controller = MusicController(
            provider = provider,
            eventProbability = 1.0f,
            highlightDurationMs = 100
        )

        controller.loadPlaylist("pl1")
        controller.loadEventTracks(eventTracks)
        controller.play()

        // 곡1 끝 → 이벤트곡
        controller.handleTrackEnd()
        // 이벤트곡 끝 → 곡2
        controller.handleTrackEnd()
        // 곡2 끝 → 이벤트곡
        controller.handleTrackEnd()

        controller.stop()

        val logs = controller.getPlayLogs()
        // 곡1 + 이벤트1 + 곡2 + 이벤트2 + 정지 시 현재곡 = 최소 4개
        assertTrue(logs.size >= 4, "로그 수: ${logs.size}")

        // 이벤트곡 로그 확인
        val eventLogs = logs.filter { it.wasEventTrack }
        assertTrue(eventLogs.isNotEmpty())
    }

    // ============================================================
    // 시나리오 8: 에러 상황 — 빈 플레이리스트
    // ============================================================

    @Test
    fun `시나리오8 - 존재하지 않는 플레이리스트 로드 후에도 크래시 없음`() = runBlocking {
        controller = MusicController(provider = provider, eventProbability = 0.0f)

        controller.loadPlaylist("nonexistent")
        controller.play()  // 무시됨

        assertEquals(PlaybackState.IDLE, controller.getPlaybackState())
        assertNull(controller.getCurrentTrack())
    }
}
