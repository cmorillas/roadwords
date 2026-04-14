package com.telytec.roadwords.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.telytec.roadwords.data.AppDatabase
import com.telytec.roadwords.data.WordRepository
import com.telytec.roadwords.data.WordEntity
import com.telytec.roadwords.data.WordWithProgress
import com.telytec.roadwords.data.ProgressEntity
import com.telytec.roadwords.domain.SrsEngine
import com.telytec.roadwords.domain.StringEvaluator
import com.telytec.roadwords.services.MicManager
import com.telytec.roadwords.services.SoundManager
import com.telytec.roadwords.services.TTSManager

enum class Screen { DASHBOARD, SESSION, STATS }

data class AppState(
    val screen: Screen = Screen.DASHBOARD,
    val selectedLevels: Set<String> = setOf("B2"),
    val currentWord: WordEntity? = null,
    val currentProgress: ProgressEntity? = null,
    val direction: String = "en_to_es",
    val isListening: Boolean = false,
    val isSpeechDetected: Boolean = false,
    val volumeLevel: Float = 0f,
    val transcript: String = "",
    val feedbackMsg: String = "",
    val isCorrect: Boolean? = null,
    val correctAnswer: String = "",
    val exampleSentence: String = "",
    val turnCount: Int = 0,
    val isReviewWord: Boolean = false,
    // Mazo progress
    val mazoGraduated: Int = 0,
    val mazoSize: Int = SrsEngine.ROUND_SIZE,
    val mazoNumber: Int = 1,
    val mazoJustCompleted: Boolean = false,
    // Global stats
    val totalWords: Int = 0,
    val learnedWords: Int = 0,
    val wordList: List<WordWithProgress> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WordRepository
    private val ttsManager: TTSManager
    private val soundManager: SoundManager
    val micManager: MicManager

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private var sessionActive = false
    private var globalTurn = 0
    private var sessionJob: Job? = null
    private var mazoNumber = 1

    init {
        val wordDao = AppDatabase.getDatabase(application).wordDao()
        repository = WordRepository(application, wordDao)
        ttsManager = TTSManager(application)
        micManager = MicManager(application)
        soundManager = SoundManager(application)

        viewModelScope.launch { repository.seedDatabase() }

        // Live transcript for UI
        viewModelScope.launch {
            micManager.transcript.collect { text ->
                if (sessionActive) {
                    _state.update { it.copy(transcript = text) }
                }
            }
        }
        // Final result triggers evaluation
        viewModelScope.launch {
            micManager.finalResult.collect { candidates ->
                if (candidates.isNotEmpty() && sessionActive) {
                    micManager.clearTranscript()
                    evaluateAnswer(candidates)
                }
            }
        }
        viewModelScope.launch {
            micManager.isListening.collect { listening ->
                _state.update { it.copy(isListening = listening) }
            }
        }
        viewModelScope.launch {
            micManager.volumeLevel.collect { vol ->
                _state.update { it.copy(volumeLevel = vol) }
            }
        }
        viewModelScope.launch {
            micManager.isSpeechDetected.collect { detected ->
                _state.update { it.copy(isSpeechDetected = detected) }
            }
        }
    }

    fun toggleLevel(level: String) {
        _state.update {
            val newLevels = it.selectedLevels.toMutableSet()
            if (newLevels.contains(level)) {
                if (newLevels.size > 1) newLevels.remove(level)
            } else {
                newLevels.add(level)
            }
            it.copy(selectedLevels = newLevels)
        }
    }

    fun startDriving() {
        sessionActive = true
        globalTurn = 0
        mazoNumber = 1

        viewModelScope.launch {
            val levels = _state.value.selectedLevels.toList()
            val total = repository.getWordCountForLevels(levels)
            val learned = repository.getLearnedCount(levels)
            val mazoProgress = repository.getMazoProgress()

            // If there's a partially-complete mazo from a previous session, continue it
            val currentMazoGraduated = mazoProgress.first
            val currentMazoSize = mazoProgress.second

            _state.update { it.copy(
                screen = Screen.SESSION,
                feedbackMsg = "",
                isCorrect = null,
                totalWords = total,
                learnedWords = learned,
                mazoGraduated = currentMazoGraduated,
                mazoSize = if (currentMazoSize > 0) currentMazoSize else SrsEngine.ROUND_SIZE,
                mazoNumber = mazoNumber,
                mazoJustCompleted = false,
                turnCount = 0
            )}
            nextTurn()
        }
    }

    fun stopDriving() {
        sessionActive = false
        sessionJob?.cancel()
        sessionJob = null
        micManager.stopListening()
        ttsManager.shutdown()
        _state.update { it.copy(screen = Screen.DASHBOARD, currentWord = null, isListening = false, volumeLevel = 0f) }
    }

    fun onPause() {
        if (sessionActive) {
            micManager.stopListening()
            sessionJob?.cancel()
            sessionJob = null
            _state.update { it.copy(isListening = false, volumeLevel = 0f) }
        }
    }

    fun onResume() {
        if (sessionActive && _state.value.screen == Screen.SESSION && _state.value.currentWord != null) {
            nextTurn()
        }
    }

    fun showStats() {
        viewModelScope.launch {
            val levels = _state.value.selectedLevels.toList()
            val total = repository.getWordCountForLevels(levels)
            val learned = repository.getLearnedCount(levels)
            val words = repository.getWordsWithProgress(levels)
            _state.update { it.copy(
                screen = Screen.STATS,
                totalWords = total,
                learnedWords = learned,
                wordList = words
            )}
        }
    }

    fun backToDashboard() {
        _state.update { it.copy(screen = Screen.DASHBOARD) }
    }

    private fun nextTurn() {
        if (!sessionActive) return
        sessionJob = viewModelScope.launch {
            globalTurn++

            _state.update { it.copy(
                isListening = false,
                isCorrect = null,
                feedbackMsg = "",
                transcript = "",
                correctAnswer = "",
                exampleSentence = "",
                isReviewWord = false,
                mazoJustCompleted = false,
                turnCount = it.turnCount + 1
            )}

            val levels = _state.value.selectedLevels.toList()

            // 10% chance to show a review word (bonus, doesn't affect mazo)
            if (Math.random() < SrsEngine.REVIEW_CHANCE) {
                val reviewWord = repository.getReviewWord()
                if (reviewWord != null) {
                    presentWord(reviewWord, isReview = true)
                    return@launch
                }
            }

            // Get next word from the mazo
            val nextWord = repository.getNextMazoWord(levels, globalTurn)

            if (nextWord == null) {
                // Mazo is complete! This shouldn't happen here (caught in evaluateAnswer),
                // but handle it as a safety net
                handleMazoComplete()
                return@launch
            }

            presentWord(nextWord, isReview = false)
        }
    }

    private suspend fun presentWord(word: WordEntity, isReview: Boolean) {
        val progress = repository.getProgressForWord(word.id)
        val direction = SrsEngine.getDirection(progress) ?: "en_to_es"

        _state.update { it.copy(
            currentWord = word,
            currentProgress = progress,
            direction = direction,
            isReviewWord = isReview
        )}

        // Update mazo progress in UI
        val mazoProgress = repository.getMazoProgress()
        _state.update { it.copy(
            mazoGraduated = mazoProgress.first,
            mazoSize = mazoProgress.second
        )}

        // TTS: say the prompt word
        if (direction == "en_to_es") {
            ttsManager.speakAndWait(word.english, isEnglish = true)
        } else {
            ttsManager.speakAndWait(word.spanish, isEnglish = false)
        }

        delay(600)
        if (!sessionActive) return

        val expectedAnswers = if (direction == "en_to_es") word.allSpanish() else word.allEnglish()
        micManager.startListening(
            isEnglish = (direction == "es_to_en"),
            biasPhrases = expectedAnswers
        )
        _state.update { it.copy(isListening = true) }
    }

    private fun evaluateAnswer(userCandidates: List<String>) {
        sessionJob = viewModelScope.launch {
            micManager.stopListening()
            val current = _state.value.currentWord ?: return@launch
            val direction = _state.value.direction

            val alternatives = if (direction == "en_to_es") current.allSpanish() else current.allEnglish()
            val evaluated = userCandidates
                .filter { it.isNotBlank() }
                .map { candidate -> candidate to StringEvaluator.evaluateAgainstList(candidate, alternatives) }
            val bestEvaluation = evaluated.firstOrNull { it.second.isCorrect }
                ?: evaluated.minByOrNull { it.second.distance }
                ?: ("" to StringEvaluator.evaluateAgainstList("", alternatives))
            val userText = bestEvaluation.first
            val result = bestEvaluation.second
            _state.update { it.copy(isListening = false, transcript = userText, volumeLevel = 0f) }

            val progressBefore = repository.getProgressForWord(current.id)
            val wasLearned = SrsEngine.isLearned(progressBefore)

            val updatedProgress = if (result.isCorrect) {
                SrsEngine.applyCorrect(progressBefore, direction, globalTurn)
            } else {
                SrsEngine.applyFail(progressBefore, direction, globalTurn)
            }
            repository.updateProgress(updatedProgress)

            val isNowLearned = SrsEngine.isLearned(updatedProgress)
            val justGraduated = !wasLearned && isNowLearned

            val allAlts = alternatives.joinToString(", ")
            val example = if (result.isCorrect) {
                current.rotatedEnglishExample(updatedProgress.totalReviews)
            } else {
                ""
            }
            val feedback = when {
                justGraduated -> "🎉 ¡Graduada!"
                result.isCorrect -> "✓ Correcto"
                else -> "✗ Incorrecto"
            }

            // Play sound
            when {
                justGraduated -> soundManager.playLearned()
                result.isCorrect -> soundManager.playCorrect()
                else -> soundManager.playIncorrect()
            }

            // Update mazo progress
            val mazoProgress = repository.getMazoProgress()
            val newLearnedWords = if (justGraduated) _state.value.learnedWords + 1 else _state.value.learnedWords

            _state.update {
                it.copy(
                    isCorrect = result.isCorrect,
                    correctAnswer = allAlts,
                    exampleSentence = example,
                    feedbackMsg = feedback,
                    currentProgress = updatedProgress,
                    learnedWords = newLearnedWords,
                    mazoGraduated = mazoProgress.first,
                    mazoSize = mazoProgress.second
                )
            }

            // Voice feedback
            if (result.isCorrect) {
                // Read english example sentence for context reinforcement
                if (example.isNotBlank()) {
                    delay(800)
                    ttsManager.speakAndWait(example, isEnglish = true)
                }
            } else {
                // Read the correct answer aloud
                delay(800)
                val correctText = alternatives.first()
                val isEnglish = direction == "es_to_en"
                ttsManager.speakAndWait(correctText, isEnglish = isEnglish)
            }

            // Check if mazo is complete after this answer
            if (repository.isMazoComplete()) {
                delay(1500)
                handleMazoComplete()
                return@launch
            }

            // Normal delay before next word
            val waitMs = when {
                justGraduated -> 2500L
                result.isCorrect -> 1500L
                else -> 2000L
            }
            delay(waitMs)
            if (sessionActive) nextTurn()
        }
    }

    private suspend fun handleMazoComplete() {
        mazoNumber++

        // Play celebration
        soundManager.playLearned()

        // TTS celebration
        ttsManager.speakAndWait("¡Mazo completado! Muy bien.", isEnglish = false)

        _state.update { it.copy(
            mazoJustCompleted = true,
            feedbackMsg = "🏆 ¡Mazo completado!",
            isCorrect = true,
            mazoGraduated = _state.value.mazoSize
        )}

        // Pause to let the user enjoy the moment
        delay(3000)

        if (!sessionActive) return

        // Start new mazo
        val levels = _state.value.selectedLevels.toList()
        repository.startNewMazo(levels)
        val mazoProgress = repository.getMazoProgress()

        _state.update { it.copy(
            mazoNumber = mazoNumber,
            mazoGraduated = mazoProgress.first,
            mazoSize = mazoProgress.second,
            mazoJustCompleted = false
        )}

        nextTurn()
    }

    override fun onCleared() {
        super.onCleared()
        sessionActive = false
        ttsManager.shutdown()
        micManager.destroy()
        soundManager.release()
    }
}
