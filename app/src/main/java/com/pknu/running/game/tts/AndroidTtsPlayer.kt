package com.pknu.running.game.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Android 내장 TextToSpeech 기반 나레이션 재생기.
 *
 * 한국어 로케일로 초기화하며, 준비 완료 전 요청은 무시한다.
 */
class AndroidTtsPlayer(context: Context) : TtsPlayer {

    @Volatile
    private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    override val isReady: Boolean get() = ready

    override fun speak(text: String) {
        if (!ready) return
        // QUEUE_FLUSH: 새 나레이션이 오면 이전 것을 끊고 즉시 재생 (우선순위 처리는 상위에서)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
