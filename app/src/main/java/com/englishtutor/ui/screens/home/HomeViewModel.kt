package com.englishtutor.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.session.AppSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val level: String = "A1",
    val lessons: List<Lesson> = emptyList(),
    val completedLessonIds: Set<String> = emptySet(),
    val bluetoothStatus: String = "Bluetooth: …",
    val isStopping: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val lessonRepository: LessonRepository,
    private val progressRepository: ProgressRepository,
    private val bluetoothConnectionMonitor: BluetoothConnectionMonitor,
    private val appSessionManager: AppSessionManager,
) : ViewModel() {

    private val lessonState = MutableStateFlow(
        HomeUiState(),
    )
    private val isStopping = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        lessonState,
        bluetoothConnectionMonitor.snapshot,
        isStopping,
    ) { local, bt, stopping ->
        local.copy(
            bluetoothStatus = "Bluetooth: ${bt.statusLabel}",
            isStopping = stopping,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

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
                    lessonState.value = state
                }
        }
    }

    fun onScreenVisible() {
        bluetoothConnectionMonitor.ensureStarted(appContext)
    }

    fun stopApp() {
        if (isStopping.value) {
            return
        }
        viewModelScope.launch {
            isStopping.value = true
            appSessionManager.stopApp()
        }
    }
}
