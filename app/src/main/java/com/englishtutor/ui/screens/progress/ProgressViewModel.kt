package com.englishtutor.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.domain.model.ProgressSummary
import com.englishtutor.domain.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProgressUiState(
    val summary: ProgressSummary = ProgressSummary(
        totalLessons = 0,
        completedLessons = 0,
        completionPercent = 0,
        testResults = emptyList(),
        completedLessonIds = emptyList(),
    ),
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressUiState())
    val uiState: StateFlow<ProgressUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressRepository.observeProgressSummary().collect { summary ->
                _uiState.update { it.copy(summary = summary) }
            }
        }
    }
}
