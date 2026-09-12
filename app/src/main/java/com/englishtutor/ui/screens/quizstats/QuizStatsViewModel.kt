package com.englishtutor.ui.screens.quizstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.stats.VoiceQuizStatsFile
import com.englishtutor.data.stats.VoiceQuizStatsStore
import com.englishtutor.data.supabase.SupabaseStatsRepository
import com.englishtutor.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizStatsBusy {
    None,
    Upload,
    Download,
}

data class QuizStatsUiState(
    val stats: VoiceQuizStatsFile = VoiceQuizStatsFile(),
    val filePath: String = "",
    val busy: Boolean = false,
    val busyAction: QuizStatsBusy = QuizStatsBusy.None,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class QuizStatsViewModel @Inject constructor(
    private val statsStore: VoiceQuizStatsStore,
    private val supabaseStatsRepository: SupabaseStatsRepository,
    private val logger: AppLogger,
) : ViewModel() {

    private val local = MutableStateFlow(
        QuizStatsUiState(filePath = statsStore.filePath),
    )

    val uiState: StateFlow<QuizStatsUiState> = combine(
        local,
        statsStore.stats,
    ) { ui, stats ->
        ui.copy(stats = stats, filePath = statsStore.filePath)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = local.value.copy(stats = statsStore.stats.value),
    )

    fun refresh() {
        viewModelScope.launch {
            statsStore.reload()
            local.update { it.copy(statusMessage = "Обновлено", errorMessage = null) }
        }
    }

    fun clearLocal() {
        viewModelScope.launch {
            statsStore.clear()
            local.update {
                it.copy(statusMessage = "Локальная статистика очищена", errorMessage = null)
            }
        }
    }

    fun uploadToServer() {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update {
                it.copy(
                    busy = true,
                    busyAction = QuizStatsBusy.Upload,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
            supabaseStatsRepository.uploadStats()
                .onSuccess { msg ->
                    logger.i(TAG, msg)
                    local.update {
                        it.copy(busy = false, busyAction = QuizStatsBusy.None, statusMessage = msg)
                    }
                }
                .onFailure { error ->
                    logger.e(TAG, "Upload failed: ${error.message}")
                    local.update {
                        it.copy(
                            busy = false,
                            busyAction = QuizStatsBusy.None,
                            errorMessage = error.message ?: "Ошибка отправки",
                        )
                    }
                }
        }
    }

    fun downloadFromServer() {
        if (local.value.busy) return
        viewModelScope.launch {
            local.update {
                it.copy(
                    busy = true,
                    busyAction = QuizStatsBusy.Download,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
            supabaseStatsRepository.downloadAndApply()
                .onSuccess { applied ->
                    local.update {
                        it.copy(
                            busy = false,
                            busyAction = QuizStatsBusy.None,
                            statusMessage = "Применено с сервера: ${applied.sessions.size} сессий",
                        )
                    }
                }
                .onFailure { error ->
                    logger.e(TAG, "Download failed: ${error.message}")
                    local.update {
                        it.copy(
                            busy = false,
                            busyAction = QuizStatsBusy.None,
                            errorMessage = error.message ?: "Ошибка загрузки",
                        )
                    }
                }
        }
    }

    companion object {
        private const val TAG = "QuizStats"
    }
}
