package com.pknu.running.music

import kotlinx.coroutines.runBlocking
import com.pknu.running.music.provider.MockMusicAdapter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioDuckerTest {

    private lateinit var provider: MockMusicAdapter
    private lateinit var ducker: AudioDucker

    @BeforeEach
    fun setUp() {
        provider = MockMusicAdapter()
        ducker = AudioDucker(
            provider = provider,
            duckedVolume = 0.3f,
            fadeDurationMs = 60,  // 테스트용으로 짧게
            fadeSteps = 3
        )
    }

    @Test
    fun `duck 호출 시 볼륨이 0_3으로 낮아진다`() = runBlocking {
        provider.setVolume(1.0f)

        ducker.duck()

        assertEquals(0.3f, provider.getVolume(), 0.01f)
        assertTrue(ducker.isDucked())
    }

    @Test
    fun `restore 호출 시 원래 볼륨으로 복구된다`() = runBlocking {
        provider.setVolume(1.0f)

        ducker.duck()
        ducker.restore()

        assertEquals(1.0f, provider.getVolume(), 0.01f)
        assertFalse(ducker.isDucked())
    }

    @Test
    fun `이미 ducked 상태에서 duck 재호출은 무시된다`() = runBlocking {
        provider.setVolume(0.8f)

        ducker.duck()
        val volumeAfterFirstDuck = provider.getVolume()

        ducker.duck()  // 중복 호출

        assertEquals(volumeAfterFirstDuck, provider.getVolume(), 0.01f)
    }

    @Test
    fun `ducked 아닌 상태에서 restore는 무시된다`() = runBlocking {
        provider.setVolume(1.0f)

        ducker.restore()  // ducked 아님

        assertEquals(1.0f, provider.getVolume(), 0.01f)
        assertFalse(ducker.isDucked())
    }

    @Test
    fun `원래 볼륨이 0_8이면 restore 후 0_8로 복구된다`() = runBlocking {
        provider.setVolume(0.8f)

        ducker.duck()
        assertEquals(0.3f, provider.getVolume(), 0.01f)

        ducker.restore()
        assertEquals(0.8f, provider.getVolume(), 0.01f)
    }

    @Test
    fun `duck과 restore를 반복해도 정상 동작한다`() = runBlocking {
        provider.setVolume(1.0f)

        ducker.duck()
        ducker.restore()
        ducker.duck()
        ducker.restore()

        assertEquals(1.0f, provider.getVolume(), 0.01f)
        assertFalse(ducker.isDucked())
    }
}
