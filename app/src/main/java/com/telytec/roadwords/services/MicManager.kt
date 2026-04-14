package com.telytec.roadwords.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

class MicManager(private val context: Context) : RecognitionListener {
    private var recognizer: SpeechRecognizer? = null
    private var lastLocale: String = "es-ES"
    private var biasPhrases: List<String> = emptyList()
    private var active = false

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _finalResult = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    val finalResult: SharedFlow<List<String>> = _finalResult.asSharedFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel

    /** True when the recognizer detects human speech (not just noise). Fires before partial results. */
    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(this)
        }
    }

    fun startListening(isEnglish: Boolean, biasPhrases: List<String> = emptyList()) {
        active = true
        lastLocale = if (isEnglish) "en-US" else "es-ES"
        this.biasPhrases = biasPhrases
        _isSpeechDetected.value = false
        ensureRecognizer()
        val intent = buildIntent()
        _isListening.value = true
        recognizer?.startListening(intent)
    }

    private fun buildIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lastLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lastLocale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lastLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
            if (biasPhrases.isNotEmpty()) {
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    ArrayList(biasPhrases.distinct().take(20))
                )
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
        }
    }

    private fun restartListening() {
        if (!active) return
        _isSpeechDetected.value = false
        try {
            recognizer?.startListening(buildIntent())
        } catch (e: Exception) {
            _isListening.value = false
        }
    }

    fun stopListening() {
        active = false
        _isListening.value = false
        _volumeLevel.value = 0f
        _isSpeechDetected.value = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (_: Exception) {}
    }

    fun destroy() {
        stopListening()
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun clearTranscript() {
        _transcript.value = ""
    }

    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
        _isSpeechDetected.value = false
    }

    /** System detected human speech (before any transcription). This is the instant feedback signal. */
    override fun onBeginningOfSpeech() {
        _isSpeechDetected.value = true
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val candidates = matches.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val text = candidates.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            _transcript.value = text
            _finalResult.tryEmit(candidates)
        }
        _isListening.value = false
        _volumeLevel.value = 0f
        _isSpeechDetected.value = false
    }

    override fun onError(error: Int) {
        if (!active) return
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            restartListening()
            return
        }
        _isListening.value = false
        _volumeLevel.value = 0f
        _isSpeechDetected.value = false
    }

    override fun onEndOfSpeech() { /* wait for onResults */ }

    override fun onPartialResults(partialResults: Bundle?) {
        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = partial?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            _transcript.value = text
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        _volumeLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
