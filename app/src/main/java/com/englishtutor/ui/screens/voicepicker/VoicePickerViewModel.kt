package com.englishtutor.ui.screens.voicepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.voice.TtsVoicePreferences
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.domain.voice.TtsVoiceOption
import com.englishtutor.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoicePickerUiState(
    val voices: List<TtsVoiceOption> = emptyList(),
    val allVoices: List<TtsVoiceOption> = emptyList(),
    val selectedRuVoiceId: String? = null,
    val selectedEnVoiceId: String? = null,
    val selectedRuDisplayName: String? = null,
    val selectedEnDisplayName: String? = null,
    val languageFilter: String? = "ru",
    val statusMessage: String? = null,
    val isLoading: Boolean = false,
) {
    val selectedVoiceIdForFilter: String?
        get() = when (languageFilter) {
            "ru" -> selectedRuVoiceId
            "en" -> selectedEnVoiceId
            else -> null
        }
}

@HiltViewModel
class VoicePickerViewModel @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val logger: AppLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoicePickerUiState())
    val uiState: StateFlow<VoicePickerUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Загрузка голосов…") }
            runCatching {
                val all = textToSpeech.listVoices(null)
                val filter = _uiState.value.languageFilter
                val filtered = if (filter.isNullOrBlank()) {
                    all
                } else {
                    all.filter {
                        it.languageTag.lowercase().startsWith(filter) ||
                            it.languageTag.substringBefore('-').equals(filter, ignoreCase = true)
                    }
                }
                all to filtered
            }.onSuccess { (all, filtered) ->
                val ruId = textToSpeech.preferredVoiceId("ru")
                val enId = textToSpeech.preferredVoiceId("en")
                _uiState.update {
                    it.copy(
                        allVoices = all,
                        voices = filtered,
                        selectedRuVoiceId = ruId,
                        selectedEnVoiceId = enId,
                        selectedRuDisplayName = displayNameFor(all, ruId),
                        selectedEnDisplayName = displayNameFor(all, enId),
                        isLoading = false,
                        statusMessage = "Голосов: ${filtered.size}",
                    )
                }
            }.onFailure { error ->
                logger.e(TAG, "listVoices failed: ${error.message}")
                _uiState.update {
                    it.copy(isLoading = false, statusMessage = error.message)
                }
            }
        }
    }

    fun setLanguageFilter(filter: String?) {
        _uiState.update { it.copy(languageFilter = filter) }
        load()
    }

    fun selectVoice(voiceId: String) {
        val voice = _uiState.value.allVoices.firstOrNull { it.id == voiceId }
            ?: _uiState.value.voices.firstOrNull { it.id == voiceId }
        val lang = TtsVoicePreferences.normalizeLang(voice?.languageTag.orEmpty())
            ?: _uiState.value.languageFilter
            ?: "ru"
        textToSpeech.setPreferredVoiceId(voiceId, lang)
        val name = voice?.displayName ?: voiceId
        _uiState.update {
            when (lang) {
                "en" -> it.copy(
                    selectedEnVoiceId = voiceId,
                    selectedEnDisplayName = name,
                    statusMessage = "EN: $name",
                )
                else -> it.copy(
                    selectedRuVoiceId = voiceId,
                    selectedRuDisplayName = name,
                    statusMessage = "RU: $name",
                )
            }
        }
    }

    fun clearPreferred() {
        val filter = _uiState.value.languageFilter
        textToSpeech.clearPreferredVoice(filter)
        _uiState.update {
            when (filter) {
                "ru" -> it.copy(
                    selectedRuVoiceId = null,
                    selectedRuDisplayName = null,
                    statusMessage = "RU: системный по умолчанию",
                )
                "en" -> it.copy(
                    selectedEnVoiceId = null,
                    selectedEnDisplayName = null,
                    statusMessage = "EN: системный по умолчанию",
                )
                else -> it.copy(
                    selectedRuVoiceId = null,
                    selectedEnVoiceId = null,
                    selectedRuDisplayName = null,
                    selectedEnDisplayName = null,
                    statusMessage = "Системные голоса по умолчанию",
                )
            }
        }
    }

    fun preview(voiceId: String) {
        viewModelScope.launch {
            selectVoice(voiceId)
            val voice = _uiState.value.voices.firstOrNull { it.id == voiceId }
                ?: _uiState.value.allVoices.firstOrNull { it.id == voiceId }
            val lang = voice?.languageTag
                ?: if (_uiState.value.languageFilter == "en") "en-US" else "ru-RU"
            val sample = if (lang.startsWith("ru", ignoreCase = true)) {
                "Привет! Это выбранный голос."
            } else {
                "Hello! This is the selected voice."
            }
            runCatching { textToSpeech.speak(sample, lang) }
                .onFailure { logger.w(TAG, "Preview failed: ${it.message}") }
        }
    }

    private fun displayNameFor(all: List<TtsVoiceOption>, id: String?): String? {
        if (id.isNullOrBlank()) return null
        return all.firstOrNull { it.id == id }?.displayName ?: id
    }

    companion object {
        private const val TAG = "VoicePicker"
    }
}
