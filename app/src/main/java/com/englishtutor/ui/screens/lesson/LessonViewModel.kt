package com.englishtutor.ui.screens.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.domain.model.LessonPhase
import com.englishtutor.domain.model.LessonSessionState
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.session.LessonSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LessonUiState(
    val isLoading: Boolean = true,
    val lessonTitle: String = "",
    val lessonContent: String = "",
    val currentPhrase: String = "",
    val phraseProgress: String = "0/0",
    val phaseLabel: String = "",
    val phraseSpoken: Boolean = false,
    val isSpeaking: Boolean = false,
    val isRecording: Boolean = false,
    val isBusy: Boolean = false,
    val lastRecognizedText: String? = null,
    val pronunciationScore: Float? = null,
    val statusMessage: String? = null,
    val isCompleted: Boolean = false,
    val isSessionActive: Boolean = false,
    val audioFocusHeld: Boolean = false,
    val audioPermissionGranted: Boolean = false,
)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val lessonRepository: LessonRepository,
    private val sessionController: LessonSessionController,
) : ViewModel() {

    val uiState: StateFlow<LessonUiState> = sessionController.state
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LessonUiState(),
        )

    fun loadLesson(lessonId: String) {
        viewModelScope.launch {
            lessonRepository.ensureContentLoaded()
            // Session service also loads; this just ensures assets are seeded before start.
            lessonRepository.getLesson(lessonId)
        }
    }

    fun onAudioPermissionResult(granted: Boolean) {
        // Mirrored in status; recognition fails gracefully with TTS if denied.
        if (!granted) {
            // no-op: controller reports via speakFeedback when recording is attempted
        }
    }

    fun onPlayPause() = sessionController.onPlayPause()

    fun onNext() = sessionController.onNext()

    fun onPrevious() = sessionController.onPrevious()

    private fun LessonSessionState.toUiState(): LessonUiState {
        val speaking = phase == LessonPhase.SPEAKING_PHRASE || phase == LessonPhase.SPEAKING_FEEDBACK
        val recording = phase == LessonPhase.RECORDING
        return LessonUiState(
            isLoading = lessonId == null,
            lessonTitle = lessonTitle,
            lessonContent = lessonContent,
            currentPhrase = currentPhrase,
            phraseProgress = phraseProgressLabel,
            phaseLabel = phase.name,
            phraseSpoken = phraseSpoken,
            isSpeaking = speaking,
            isRecording = recording,
            isBusy = speaking || recording,
            lastRecognizedText = lastRecognizedText,
            pronunciationScore = pronunciationScore,
            statusMessage = statusMessage,
            isCompleted = isCompleted || phase == LessonPhase.COMPLETED,
            isSessionActive = isActive,
            audioFocusHeld = audioFocusHeld,
        )
    }
}
