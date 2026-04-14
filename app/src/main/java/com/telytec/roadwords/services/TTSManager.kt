package com.telytec.roadwords.services

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private var doneSignal: CompletableDeferred<Unit>? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { _isSpeaking.value = true }
                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    doneSignal?.complete(Unit)
                }
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    doneSignal?.complete(Unit)
                }
            })
        }
    }

    /**
     * Speak text and suspend until TTS has completely finished playing audio.
     */
    suspend fun speakAndWait(text: String, isEnglish: Boolean) {
        if (!isInitialized) return
        val locale = if (isEnglish) Locale.US else Locale("es", "ES")
        tts?.language = locale

        doneSignal = CompletableDeferred()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rw_utterance")

        // Wait until onDone/onError fires
        doneSignal?.await()
        doneSignal = null
    }

    fun speak(text: String, isEnglish: Boolean) {
        if (!isInitialized) return
        val locale = if (isEnglish) Locale.US else Locale("es", "ES")
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rw_utterance")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        doneSignal?.complete(Unit)
        doneSignal = null
    }
}
