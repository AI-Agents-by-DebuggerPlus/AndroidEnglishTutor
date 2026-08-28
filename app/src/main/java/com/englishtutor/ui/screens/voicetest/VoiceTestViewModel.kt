package com.englishtutor.ui.screens.voicetest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.session.HeadsetTestController
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceTestUiState(
    val versionLabel: String = AppVersion.label,
    val selectedTab: Int = 1,
    val speakText: String = "Hello, how are you?",
    val languageCode: String = "en-US",
    val recognizedText: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isSpeaking: Boolean = false,
    val isRecording: Boolean = false,
    val micGranted: Boolean = false,
    val ttsAvailable: Boolean = false,
    val sttAvailable: Boolean = false,
    val headsetActive: Boolean = false,
    val btPressCount: Int = 0,
    val btLastEventLabel: String = "",
    val btLastEventAt: String = "",
    val btEventLog: List<String> = emptyList(),
    val headsetStatus: String? = null,
) {
    val isBusy: Boolean get() = isSpeaking || isRecording
}

@HiltViewModel
class VoiceTestViewModel @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val speechRecognizer: SpeechRecognizerProvider,
    private val headsetTestController: HeadsetTestController,
    private val logger: AppLogger,
) : ViewModel() {

    private val localState = MutableStateFlow(
        VoiceTestUiState(
            ttsAvailable = textToSpeech.isAvailable(),
            sttAvailable = speechRecognizer.isAvailable(),
        ),
    )

    val uiState: StateFlow<VoiceTestUiState> = combine(
        localState,
        headsetTestController.state,
    ) { local, headset ->
        local.copy(
            headsetActive = headset.isActive,
            btPressCount = headset.pressCount,
            btLastEventLabel = headset.lastEventLabel,
            btLastEventAt = headset.lastEventAt,
            btEventLog = headset.eventLog,
            headsetStatus = headset.statusMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = localState.value,
    )

    init {
        logger.i(TAG, "Voice test opened · ${AppVersion.label}")
    }

    fun onSpeakTextChanged(value: String) {
        localState.update { it.copy(speakText = value) }
    }

    fun onLanguageChanged(value: String) {
        localState.update { it.copy(languageCode = value) }
    }

    fun onMicPermission(granted: Boolean) {
        localState.update { it.copy(micGranted = granted) }
        logger.i(TAG, "Mic permission: $granted")
        if (!granted) {
            localState.update { it.copy(errorMessage = "Нужен доступ к микрофону") }
        }
    }

    fun selectTab(index: Int) {
        localState.update { it.copy(selectedTab = index.coerceIn(0, 2)) }
    }

    fun resetBtPlayCounter() = headsetTestController.resetCounter()

    fun simulateBtPlay() = headsetTestController.simulatePlay()

    fun speak() {
        val text = localState.value.speakText.trim()
        val lang = localState.value.languageCode.trim().ifBlank { "en-US" }
        if (text.isEmpty()) {
            localState.update { it.copy(errorMessage = "Введите текст") }
            return
        }
        viewModelScope.launch {
            localState.update {
                it.copy(isSpeaking = true, errorMessage = null, statusMessage = "Озвучка…")
            }
            logger.i(TAG, "TTS start lang=$lang text=\"$text\"")
            try {
                textToSpeech.speak(text, lang)
                logger.i(TAG, "TTS done")
                localState.update { it.copy(statusMessage = "Озвучка завершена") }
            } catch (error: Exception) {
                logger.e(TAG, "TTS error: ${error.message}")
                localState.update { it.copy(errorMessage = error.message ?: "Ошибка TTS") }
            } finally {
                localState.update { it.copy(isSpeaking = false) }
            }
        }
    }

    fun recognize() {
        if (!localState.value.micGranted) {
            localState.update { it.copy(errorMessage = "Нет доступа к микрофону") }
            logger.w(TAG, "STT blocked: no mic permission")
            return
        }
        if (!speechRecognizer.isAvailable()) {
            localState.update { it.copy(errorMessage = "Распознавание недоступно") }
            logger.w(TAG, "STT not available")
            return
        }
        val lang = localState.value.languageCode.trim().ifBlank { "en-US" }
        viewModelScope.launch {
            localState.update {
                it.copy(
                    isRecording = true,
                    errorMessage = null,
                    statusMessage = "Говорите…",
                    recognizedText = null,
                )
            }
            logger.i(TAG, "STT start lang=$lang")
            speechRecognizer.recognize(lang)
                .onSuccess { spoken ->
                    logger.i(TAG, "STT result=\"$spoken\"")
                    localState.update {
                        it.copy(
                            isRecording = false,
                            recognizedText = spoken,
                            statusMessage = "Распознавание завершено",
                        )
                    }
                }
                .onFailure { error ->
                    logger.e(TAG, "STT error: ${error.message}")
                    localState.update {
                        it.copy(
                            isRecording = false,
                            errorMessage = error.message ?: "Ошибка STT",
                        )
                    }
                }
        }
    }

    fun speakThenRecognize() {
        val text = localState.value.speakText.trim()
        val lang = localState.value.languageCode.trim().ifBlank { "en-US" }
        if (text.isEmpty()) {
            localState.update { it.copy(errorMessage = "Введите текст") }
            return
        }
        if (!localState.value.micGranted) {
            localState.update { it.copy(errorMessage = "Нет доступа к микрофону") }
            return
        }
        viewModelScope.launch {
            localState.update {
                it.copy(isSpeaking = true, errorMessage = null, statusMessage = "Озвучка…")
            }
            logger.i(TAG, "TTS→STT start")
            try {
                textToSpeech.speak(text, lang)
            } catch (error: Exception) {
                logger.e(TAG, "TTS error: ${error.message}")
                localState.update {
                    it.copy(isSpeaking = false, errorMessage = error.message ?: "Ошибка TTS")
                }
                return@launch
            }
            localState.update {
                it.copy(
                    isSpeaking = false,
                    isRecording = true,
                    statusMessage = "Говорите…",
                    recognizedText = null,
                )
            }
            speechRecognizer.recognize(lang)
                .onSuccess { spoken ->
                    logger.i(TAG, "TTS→STT result=\"$spoken\"")
                    localState.update {
                        it.copy(
                            isRecording = false,
                            recognizedText = spoken,
                            statusMessage = "Цикл завершён",
                        )
                    }
                }
                .onFailure { error ->
                    logger.e(TAG, "TTS→STT STT error: ${error.message}")
                    localState.update {
                        it.copy(
                            isRecording = false,
                            errorMessage = error.message ?: "Ошибка STT",
                        )
                    }
                }
        }
    }

    companion object {
        private const val TAG = "VoiceTest"
    }
}
