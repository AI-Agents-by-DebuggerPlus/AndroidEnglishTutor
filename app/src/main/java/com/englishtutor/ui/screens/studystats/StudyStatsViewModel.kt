package com.englishtutor.ui.screens.studystats

import androidx.lifecycle.ViewModel
import com.englishtutor.data.topics.TopicRepository
import com.englishtutor.data.voice.StudySessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StudyStatsUiState(
    val wordsViewed: Int = 0,
    val phrasesViewed: Int = 0,
    val sentencesViewed: Int = 0,
    val totalCardsViewed: Int = 0,
    val viewedTopics: Int = 0,
    val totalTopics: Int = 0,
)

@HiltViewModel
class StudyStatsViewModel @Inject constructor(
    private val sessionPrefs: StudySessionPreferences,
    private val topicRepository: TopicRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyStatsUiState())
    val uiState: StateFlow<StudyStatsUiState> = _uiState.asStateFlow()

    fun refresh() {
        val words = sessionPrefs.studiedWordsCount()
        val phrases = sessionPrefs.studiedPhrasesCount()
        val sentences = sessionPrefs.studiedSentencesCount()
        _uiState.update {
            StudyStatsUiState(
                wordsViewed = words,
                phrasesViewed = phrases,
                sentencesViewed = sentences,
                totalCardsViewed = words + phrases + sentences,
                viewedTopics = sessionPrefs.viewedTopicIds().size,
                totalTopics = topicRepository.listTopics().size,
            )
        }
    }
}
