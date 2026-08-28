package com.englishtutor.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.supabase.SupabaseLogRepository
import com.englishtutor.data.supabase.SupabaseSettingsRepository
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import com.englishtutor.util.LogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LogsUiState(
    val versionLabel: String = AppVersion.label,
    val entries: List<LogEntry> = emptyList(),
    val isUploading: Boolean = false,
    val uploadStatus: String? = null,
    val uploadError: String? = null,
    val recipientLabel: String = "WpfChat",
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logger: AppLogger,
    private val supabaseLogRepository: SupabaseLogRepository,
    settingsRepository: SupabaseSettingsRepository,
) : ViewModel() {

    private val uploadState = MutableStateFlow(UploadUi())

    val uiState: StateFlow<LogsUiState> = combine(
        logger.entries,
        uploadState,
    ) { entries, upload ->
        LogsUiState(
            versionLabel = AppVersion.label,
            entries = entries,
            isUploading = upload.isUploading,
            uploadStatus = upload.status,
            uploadError = upload.error,
            recipientLabel = settingsRepository.getSettings().logRecipientName
                .ifBlank { "WpfChat" },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogsUiState(
            entries = logger.entries.value,
            recipientLabel = settingsRepository.getSettings().logRecipientName.ifBlank { "WpfChat" },
        ),
    )

    fun clear() {
        logger.clear()
        supabaseLogRepository.clearSentMarks()
        uploadState.update { it.copy(status = "Логи очищены", error = null) }
    }

    fun sendToServer() {
        if (uploadState.value.isUploading) return
        val entries = logger.entries.value
        if (entries.isEmpty()) {
            uploadState.update {
                it.copy(status = null, error = "Буфер логов пуст")
            }
            return
        }
        viewModelScope.launch {
            uploadState.update {
                it.copy(isUploading = true, status = "Отправка на сервер…", error = null)
            }
            logger.i("Supabase", "Manual upload start, entries=${entries.size}")
            supabaseLogRepository.sendBuffer(entries)
                .onSuccess { result ->
                    uploadState.update {
                        it.copy(
                            isUploading = false,
                            status = "Готово: ${result.summary}",
                            error = null,
                        )
                    }
                }
                .onFailure { error ->
                    logger.e("Supabase", "Upload failed: ${error.message}")
                    uploadState.update {
                        it.copy(
                            isUploading = false,
                            status = null,
                            error = error.message ?: "Ошибка отправки",
                        )
                    }
                }
        }
    }

    private data class UploadUi(
        val isUploading: Boolean = false,
        val status: String? = null,
        val error: String? = null,
    )
}
