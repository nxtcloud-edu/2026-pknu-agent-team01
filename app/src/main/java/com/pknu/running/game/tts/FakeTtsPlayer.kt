package com.pknu.running.game.tts

/**
 * 테스트/데모용 나레이션 재생기. 실제 음성 없이 재생 요청을 기록만 한다.
 */
class FakeTtsPlayer : TtsPlayer {

    val spoken = mutableListOf<String>()

    override val isReady: Boolean = true

    override fun speak(text: String) {
        spoken.add(text)
    }

    override fun shutdown() {
        spoken.clear()
    }
}
