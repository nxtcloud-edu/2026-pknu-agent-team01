package com.pknu.running.music

/**
 * TTS 재생 시 음악 볼륨을 30%로 낮추고, 종료 후 복구한다.
 * 중복 호출을 방지하며, fade 효과는 Provider의 setVolume을 단계적으로 호출하여 구현한다.
 */
class AudioDucker(
    private val provider: MusicProvider,
    private val duckedVolume: Float = 0.3f,
    private val fadeDurationMs: Long = 300,
    private val fadeSteps: Int = 6
) {

    private var isDucked = false
    private var originalVolume: Float = 1.0f

    fun isDucked(): Boolean = isDucked

    /**
     * 음악 볼륨을 duckedVolume으로 낮춘다.
     * 이미 ducked 상태면 무시한다.
     */
    suspend fun duck() {
        if (isDucked) return

        originalVolume = provider.getVolume()
        isDucked = true
        fadeVolume(from = originalVolume, to = duckedVolume)
    }

    /**
     * 음악 볼륨을 원래 값으로 복구한다.
     * ducked 상태가 아니면 무시한다.
     */
    suspend fun restore() {
        if (!isDucked) return

        isDucked = false
        fadeVolume(from = duckedVolume, to = originalVolume)
    }

    private suspend fun fadeVolume(from: Float, to: Float) {
        val stepDelay = fadeDurationMs / fadeSteps
        val stepSize = (to - from) / fadeSteps

        for (i in 1..fadeSteps) {
            val volume = from + stepSize * i
            provider.setVolume(volume.coerceIn(0.0f, 1.0f))
            kotlinx.coroutines.delay(stepDelay)
        }

        // 최종값 보정
        provider.setVolume(to.coerceIn(0.0f, 1.0f))
    }
}
