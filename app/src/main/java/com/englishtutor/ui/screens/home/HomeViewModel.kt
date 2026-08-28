package com.englishtutor.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val level: String = "A1",
    val lessons: List<Lesson> = emptyList(),
    val completedLessonIds: Set<String> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val lessonRepository: LessonRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            lessonRepository.ensureContentLoaded()
            progressRepository.observeUserProfile()
                .flatMapLatest { profile ->
                    val level = profile.placementLevel ?: "A1"
                    combine(
                        lessonRepository.observeLessonsFromLevel(level),
                        progressRepository.observeProgressSummary(),
                    ) { lessons, summary ->
                        HomeUiState(
                            level = level,
                            lessons = lessons,
                            completedLessonIds = summary.completedLessonIds.toSet(),
                        )
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }
}
