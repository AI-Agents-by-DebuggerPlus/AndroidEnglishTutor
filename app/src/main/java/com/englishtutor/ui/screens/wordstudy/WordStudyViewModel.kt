package com.englishtutor.ui.screens.wordstudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.voice.StudyFontPreferences
import com.englishtutor.session.VoiceQuizController
import com.englishtutor.session.WordStudyController
import com.englishtutor.session.WordStudyState
import com.englishtutor.ui.flashcards.FlashcardDisplaySettings
import com.englishtutor.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WordStudyUiState(
    val study: WordStudyState = WordStudyState(),
    val display: FlashcardDisplaySettings = FlashcardDisplaySettings(),
)

@HiltViewModel
class WordStudyViewModel @Inject constructor(
    private val wordStudyController: WordStudyController,
    private val voiceQuizController: VoiceQuizController,
    private val fontPreferences: StudyFontPreferences,
    private val logger: AppLogger,
) : ViewModel() {

    val uiState: StateFlow<WordStudyUiState> = combine(
        wordStudyController.state,
        fontPreferences.display,
    ) { study, display ->
        WordStudyUiState(study = study, display = display)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WordStudyUiState(
            study = wordStudyController.state.value,
            display = fontPreferences.getDisplay(),
        ),
    )

    fun onScreenVisible() {
        // Home arms the quiz — reclaim headset routing for study.
        if (voiceQuizController.isActive) {
            voiceQuizController.deactivate()
            logger.i(TAG, "Quiz disarmed for word study")
        }
        // Auto-speak current card when opening lessons from Home.
        wordStudyController.activate(autoSpeak = true)
        logger.i(TAG, "Word study screen visible · active=${wordStudyController.isActive}")
    }

    fun onScreenHidden() {
        // Keep study armed so Play/Next still work when opening voices/settings overlays.
        // Home explicitly deactivates study when it becomes visible again.
        wordStudyController.pauseSpeaking()
        logger.i(TAG, "Word study screen hidden · study stays active=${wordStudyController.isActive}")
    }

    fun endStudySession() {
        wordStudyController.deactivate()
    }

    fun onNext() {
        ensureStudyActive()
        wordStudyController.onNext("ui")
    }

    fun onPlay() {
        ensureStudyActive()
        wordStudyController.onPlay("ui")
    }

    fun selectTopic(topicId: String) {
        ensureStudyActive()
        wordStudyController.selectTopic(topicId)
    }

    fun nextTopic() {
        ensureStudyActive()
        wordStudyController.nextTopic("ui")
    }

    fun updateDisplay(settings: FlashcardDisplaySettings) =
        fontPreferences.updateDisplay(settings)

    private fun ensureStudyActive() {
        if (voiceQuizController.isActive) {
            voiceQuizController.deactivate()
        }
        if (!wordStudyController.isActive) {
            wordStudyController.activate()
        }
    }

    companion object {
        private const val TAG = "WordStudyUI"
    }
}
