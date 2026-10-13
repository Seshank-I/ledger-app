package com.simpleledger.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads sentences aloud with the phone's text-to-speech, in Indian English. Works offline. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.forLanguageTag("en-IN")
        ready = true
        pending?.let(::speak)
        pending = null
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ledger")
    }

    fun stop() {
        if (ready) tts.stop()
    }
}
