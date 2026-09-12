package com.englishtutor.ui.screens.wordstudy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.voice.StudyFontPreferences
import com.englishtutor.session.VoiceQuizController
import com.englishtutor.session.WordStudyController
import com.englishtutor.session.WordStudyState
import com.englishtutor.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WordStudyUiState(
    val study: WordStudyState = WordStudyState(),
    val enSp: Float = StudyFontPreferences.DEFAULT_EN_SP,
    val ruSp: Float = StudyFontPreferences.DEFAULT_RU_SP,
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
        fontPreferences.enSp,
        fontPreferences.ruSp,
    ) { study, en, ru ->
        WordStudyUiState(study = study, enSp = en, ruSp = ru)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordStudyUiState(),
    )

    fun onScreenVisible() {
        if (voiceQuizController.isActive) {
            voiceQuizController.deactivate()
        }
        wordStudyController.activate()
        logger.i(TAG, "Word study screen visible")
    }

    fun onScreenHidden() {
        // Keep study armed only while this screen is open.
        wordStudyController.deactivate()
    }

    fun onNext() = wordStudyController.onNext("ui")

    fun onPlay() = wordStudyController.onPlay("ui")

    fun setEnSp(value: Float) = fontPreferences.setEnSp(value)

    fun setRuSp(value: Float) = fontPreferences.setRuSp(value)

    companion object {
        private const val TAG = "WordStudyUI"
    }
}
