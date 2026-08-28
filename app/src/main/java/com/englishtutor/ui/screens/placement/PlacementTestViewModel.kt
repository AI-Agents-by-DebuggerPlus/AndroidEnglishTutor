package com.englishtutor.ui.screens.placement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.domain.model.PlacementQuestion
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.domain.util.PronunciationMatcher
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PlacementUiState(
    val isLoading: Boolean = true,
    val questions: List<PlacementQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOptionIndex: Int? = null,
    val isRecording: Boolean = false,
    val lastRecognizedText: String? = null,
    val pronunciationMatched: Boolean = false,
    val feedback: String? = null,
    val errorMessage: String? = null,
    val completed: Boolean = false,
    val resultLevel: String? = null,
    val resultScore: Int = 0,
    val audioPermissionGranted: Boolean = false,
) {
    val currentQuestion: PlacementQuestion? get() = questions.getOrNull(currentIndex)
    val totalQuestions: Int get() = questions.size
    val isLastQuestion: Boolean get() = currentIndex >= questions.lastIndex
    val canSubmit: Boolean
        get() = when (val question = currentQuestion) {
            is PlacementQuestion.MultipleChoice -> selectedOptionIndex != null
            is PlacementQuestion.Pronunciation -> pronunciationMatched
            null -> false
        }
}

@HiltViewModel
class PlacementTestViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val speechRecognizer: SpeechRecognizerProvider,
) : ViewModel() {

    private val json = Json
    private val answers = mutableListOf<AnswerRecord>()
    private val _uiState = MutableStateFlow(PlacementUiState())
    val uiState: StateFlow<PlacementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val questions = progressRepository.getPlacementQuestions()
            _uiState.update { it.copy(isLoading = false, questions = questions) }
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(audioPermissionGranted = granted) }
    }

    fun selectOption(index: Int) {
        _uiState.update { it.copy(selectedOptionIndex = index, feedback = null) }
    }

    fun recordPronunciation() {
        val question = _uiState.value.currentQuestion as? PlacementQuestion.Pronunciation ?: return
        if (!_uiState.value.audioPermissionGranted) {
            _uiState.update { it.copy(errorMessage = "Microphone permission is required") }
            return
        }
        if (!speechRecognizer.isAvailable()) {
            _uiState.update { it.copy(errorMessage = "Speech recognition is not available on this device") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true, errorMessage = null, feedback = null) }
            speechRecognizer.recognize(question.languageCode)
                .onSuccess { spoken ->
                    val matched = PronunciationMatcher.isMatch(question.expectedText, spoken)
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            lastRecognizedText = spoken,
                            pronunciationMatched = matched,
                            feedback = if (matched) "Great!" else "Try again — expected: \"${question.expectedText}\"",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRecording = false,
                            errorMessage = error.message ?: "Recognition failed",
                        )
                    }
                }
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val question = state.currentQuestion ?: return

        val isCorrect = when (question) {
            is PlacementQuestion.MultipleChoice -> state.selectedOptionIndex == question.correctIndex
            is PlacementQuestion.Pronunciation -> state.pronunciationMatched
        }

        answers += AnswerRecord(
            questionId = question.id,
            level = question.level,
            correct = isCorrect,
        )

        if (state.isLastQuestion) {
            finishTest()
        } else {
            _uiState.update {
                it.copy(
                    currentIndex = it.currentIndex + 1,
                    selectedOptionIndex = null,
                    lastRecognizedText = null,
                    pronunciationMatched = false,
                    feedback = null,
                    errorMessage = null,
                )
            }
        }
    }

    private fun finishTest() {
        val total = answers.size.coerceAtLeast(1)
        val correctCount = answers.count { it.correct }
        val score = correctCount * 100 / total
        val level = determineLevel(answers)
        val details = json.encodeToString(answers)

        viewModelScope.launch {
            progressRepository.savePlacementResult(level, score, details)
            _uiState.update {
                it.copy(
                    completed = true,
                    resultLevel = level,
                    resultScore = score,
                )
            }
        }
    }

    private fun determineLevel(answers: List<AnswerRecord>): String {
        val levelScores = answers.groupBy { it.level }.mapValues { (_, records) ->
            records.count { it.correct }.toFloat() / records.size
        }
        return when {
            (levelScores["B1"] ?: 0f) >= 0.5f -> "B1"
            (levelScores["A2"] ?: 0f) >= 0.5f -> "A2"
            else -> "A1"
        }
    }

    @kotlinx.serialization.Serializable
    private data class AnswerRecord(
        val questionId: String,
        val level: String,
        val correct: Boolean,
    )
}
