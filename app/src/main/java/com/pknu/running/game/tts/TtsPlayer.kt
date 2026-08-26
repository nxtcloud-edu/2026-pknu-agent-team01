package com.pknu.running.game.tts

/**
 * 나레이션 음성 재생 추상화.
 *
 * 실제 구현은 Android TextToSpeech를 사용하지만, 추후 사전 제작 음성 파일 재생 방식으로
 * 교체할 수 있도록 인터페이스로 분리한다 (application-design.md의 TTS 전략).
 */
interface TtsPlayer {

    /** 사용 준비 여부. */
    val isReady: Boolean

    /** 텍스트를 음성으로 재생한다. */
    fun speak(text: String)

    /** 자원 해제. */
    fun shutdown()
}
