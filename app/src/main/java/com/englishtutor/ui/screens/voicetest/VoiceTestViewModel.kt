package com.englishtutor.ui.screens.voicetest

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.bluetooth.ActiveBluetoothDevice
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.bluetooth.ConnectedBluetoothDevice
import com.englishtutor.bluetooth.ActiveMediaSessionRow
import com.englishtutor.bluetooth.ActiveSessionsHelper
import com.englishtutor.bluetooth.DiagnosticLevel
import com.englishtutor.bluetooth.HeadsetDiagnosticLine
import com.englishtutor.bluetooth.HeadsetDiagnosticsHelper
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.session.AppSessionManager
import com.englishtutor.session.HeadsetButtonNotifier
import com.englishtutor.session.HeadsetButtonPreferences
import com.englishtutor.session.HeadsetMonitorService
import com.englishtutor.session.HeadsetTestController
import com.englishtutor.session.LessonSessionService
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoiceTestUiState(
    val versionLabel: String = AppVersion.label,
    val selectedTab: Int = 2,
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
    val nativeCaptureOn: Boolean = false,
    val btPressCount: Int = 0,
    val btNextCount: Int = 0,
    val btLastEventLabel: String = "",
    val btLastEventAt: String = "",
    val btEventLog: List<String> = emptyList(),
    val debounceEnabled: Boolean = true,
    val debounceIntervalMs: Long = HeadsetButtonPreferences.DEFAULT_DEBOUNCE_MS,
    val debounceIntervalText: String = HeadsetButtonPreferences.DEFAULT_DEBOUNCE_MS.toString(),
    val nextDoubleTapMs: Long = HeadsetButtonPreferences.DEFAULT_NEXT_DOUBLE_TAP_MS,
    val nextDoubleTapText: String = HeadsetButtonPreferences.DEFAULT_NEXT_DOUBLE_TAP_MS.toString(),
    val headsetStatus: String? = null,
    val bluetoothPermissionGranted: Boolean = true,
    val connectedBluetoothDevices: List<ConnectedBluetoothDevice> = emptyList(),
    val activeBluetoothDevice: ActiveBluetoothDevice? = null,
    val taskerMayConflict: Boolean = false,
    val diagnosticLines: List<HeadsetDiagnosticLine> = emptyList(),
    val diagnosticsSummary: String = "",
    val mediaButtonPathReady: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    val activeMediaSessions: List<ActiveMediaSessionRow> = emptyList(),
    val isClosing: Boolean = false,
) {
    val isBusy: Boolean get() = isSpeaking || isRecording
}

@HiltViewModel
class VoiceTestViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val textToSpeech: TextToSpeechProvider,
    private val speechRecognizer: SpeechRecognizerProvider,
    private val headsetTestController: HeadsetTestController,
    private val headsetButtonNotifier: HeadsetButtonNotifier,
    private val headsetButtonPreferences: HeadsetButtonPreferences,
    private val bluetoothConnectionMonitor: BluetoothConnectionMonitor,
    private val headsetDiagnosticsHelper: HeadsetDiagnosticsHelper,
    private val activeSessionsHelper: ActiveSessionsHelper,
    private val appSessionManager: AppSessionManager,
    private val logger: AppLogger,
) : ViewModel() {

    private val localState = MutableStateFlow(
        VoiceTestUiState(
            ttsAvailable = textToSpeech.isAvailable(),
            sttAvailable = speechRecognizer.isAvailable(),
            debounceEnabled = headsetButtonPreferences.debounceEnabled,
            debounceIntervalMs = headsetButtonPreferences.debounceIntervalMs,
            debounceIntervalText = headsetButtonPreferences.debounceIntervalMs.toString(),
            nextDoubleTapMs = headsetButtonPreferences.nextDoubleTapMs,
            nextDoubleTapText = headsetButtonPreferences.nextDoubleTapMs.toString(),
        ),
    )

    val uiState: StateFlow<VoiceTestUiState> = combine(
        localState,
        headsetTestController.state,
        bluetoothConnectionMonitor.snapshot,
        headsetButtonPreferences.state,
    ) { local, headset, bluetooth, buttonPrefs ->
        local.copy(
            nativeCaptureOn = headset.nativeCaptureOn,
            btPressCount = headset.pressCount,
            btNextCount = headset.nextCount,
            btLastEventLabel = headset.lastEventLabel,
            btLastEventAt = headset.lastEventAt,
            btEventLog = headset.eventLog,
            debounceEnabled = buttonPrefs.debounceEnabled,
            debounceIntervalMs = buttonPrefs.debounceIntervalMs,
            nextDoubleTapMs = buttonPrefs.nextDoubleTapMs,
            headsetStatus = headset.statusMessage,
            bluetoothPermissionGranted = bluetooth.permissionGranted,
            connectedBluetoothDevices = bluetooth.devices,
            activeBluetoothDevice = bluetooth.activeDevice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = localState.value,
    )

    init {
        logger.i(TAG, "Voice test opened · ${AppVersion.label}")
        bluetoothConnectionMonitor.ensureStarted(appContext)
        bluetoothConnectionMonitor.refresh(appContext)
        viewModelScope.launch {
            while (isActive) {
                delay(BLUETOOTH_REFRESH_MS)
                bluetoothConnectionMonitor.refresh(appContext)
                if (localState.value.selectedTab == 2) {
                    refreshDiagnostics()
                }
            }
        }
        enterHeadsetIsolation()
        reassertBtPlayCapture(speakCue = true)
        viewModelScope.launch {
            delay(SERVICE_START_GRACE_MS)
            refreshDiagnostics()
        }
    }

    override fun onCleared() {
        exitHeadsetIsolation()
        super.onCleared()
    }

    private fun enterHeadsetIsolation() {
        textToSpeech.stopSpeaking()
        speechRecognizer.cancel()
        stopLessonSession()
        HeadsetMonitorService.start(appContext)
        headsetButtonNotifier.btPlayTestIsolation = true
        refreshIsolatedHandler()
        refreshDiagnostics()
        logger.i(TAG, "Headset isolation ON")
    }

    private fun exitHeadsetIsolation() {
        headsetButtonNotifier.btPlayTestIsolation = false
        headsetButtonNotifier.isolatedBtPlayHandler = null
        logger.i(TAG, "Headset isolation OFF")
    }

    private fun stopLessonSession() {
        appContext.startService(
            Intent(appContext, LessonSessionService::class.java).apply {
                action = LessonSessionService.ACTION_STOP
            },
        )
    }

    private fun refreshIsolatedHandler() {
        headsetButtonNotifier.isolatedBtPlayHandler = when (localState.value.selectedTab) {
            1 -> ({ recognize() })
            else -> null
        }
    }

    fun onSpeakTextChanged(value: String) {
        localState.update { it.copy(speakText = value) }
    }

    fun onLanguageChanged(value: String) {
        localState.update { it.copy(languageCode = value) }
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val micGranted = grants[android.Manifest.permission.RECORD_AUDIO] == true
        localState.update { it.copy(micGranted = micGranted) }
        logger.i(TAG, "Mic permission: $micGranted")
        if (!micGranted) {
            localState.update { it.copy(errorMessage = "Нужен доступ к микрофону") }
        }
        bluetoothConnectionMonitor.refresh(appContext)
        refreshDiagnostics()
    }

    fun selectTab(index: Int) {
        localState.update { it.copy(selectedTab = index.coerceIn(0, 2)) }
        refreshIsolatedHandler()
        if (index == 2) {
            reassertBtPlayCapture(speakCue = true)
            refreshDiagnostics()
        }
    }

    fun reassertBtPlayCapture(speakCue: Boolean = true) {
        HeadsetMonitorService.reassert(appContext)
        logger.i(TAG, "MediaSession reassert requested")
        if (speakCue) {
            viewModelScope.launch {
                runCatching {
                    textToSpeech.speak(BT_TEST_READY_CUE, "en-US")
                }.onFailure { error ->
                    logger.w(TAG, "BT test cue failed: ${error.message}")
                }
            }
        }
        viewModelScope.launch {
            delay(SERVICE_START_GRACE_MS)
            refreshDiagnostics()
        }
    }

    fun refreshDiagnostics() {
        val headset = headsetTestController.state.value
        val snapshot = headsetDiagnosticsHelper.collect(
            context = appContext,
            nativeCaptureOn = headset.nativeCaptureOn,
            btIsolationOn = headsetButtonNotifier.btPlayTestIsolation,
            micGranted = localState.value.micGranted,
        )
        val taskerMayConflict = snapshot.lines.any {
            it.id == "tasker_listener" && it.level == DiagnosticLevel.WARN
        }
        localState.update {
            it.copy(
                diagnosticLines = snapshot.lines,
                diagnosticsSummary = snapshot.summary,
                mediaButtonPathReady = snapshot.mediaButtonPathReady,
                notificationAccessEnabled = snapshot.notificationAccessEnabled,
                activeMediaSessions = snapshot.activeSessions,
                taskerMayConflict = taskerMayConflict,
            )
        }
    }

    fun openNotificationAccessSettings() {
        runCatching {
            appContext.startActivity(
                activeSessionsHelper.notificationAccessSettingsIntent().addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
            )
        }.onFailure { error ->
            logger.w(TAG, "Open notification access failed: ${error.message}")
        }
    }

    fun closeApp() {
        if (localState.value.isClosing) {
            return
        }
        viewModelScope.launch {
            localState.update { it.copy(isClosing = true) }
            appSessionManager.stopApp()
        }
    }

    fun resetBtPlayCounter() = headsetTestController.resetCounter()

    fun setDebounceEnabled(enabled: Boolean) {
        headsetButtonPreferences.setDebounceEnabled(enabled)
        logger.i(TAG, "Debounce enabled: $enabled")
    }

    fun onDebounceIntervalTextChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(5)
        localState.update { it.copy(debounceIntervalText = filtered) }
        filtered.toLongOrNull()?.let { parsed ->
            if (parsed >= HeadsetButtonPreferences.MIN_INTERVAL_MS) {
                headsetButtonPreferences.setDebounceIntervalMs(parsed)
            }
        }
    }

    fun commitDebounceInterval() {
        val parsed = localState.value.debounceIntervalText.toLongOrNull()
            ?: HeadsetButtonPreferences.DEFAULT_DEBOUNCE_MS
        headsetButtonPreferences.setDebounceIntervalMs(parsed)
        val applied = headsetButtonPreferences.debounceIntervalMs
        localState.update { it.copy(debounceIntervalText = applied.toString()) }
        logger.i(TAG, "Debounce interval: ${applied}ms")
    }

    fun onNextDoubleTapTextChanged(value: String) {
        val filtered = value.filter { it.isDigit() }.take(5)
        localState.update { it.copy(nextDoubleTapText = filtered) }
        filtered.toLongOrNull()?.let { parsed ->
            if (parsed >= HeadsetButtonPreferences.MIN_INTERVAL_MS) {
                headsetButtonPreferences.setNextDoubleTapMs(parsed)
            }
        }
    }

    fun commitNextDoubleTapInterval() {
        val parsed = localState.value.nextDoubleTapText.toLongOrNull()
            ?: HeadsetButtonPreferences.DEFAULT_NEXT_DOUBLE_TAP_MS
        headsetButtonPreferences.setNextDoubleTapMs(parsed)
        val applied = headsetButtonPreferences.nextDoubleTapMs
        localState.update { it.copy(nextDoubleTapText = applied.toString()) }
        logger.i(TAG, "Next double-tap interval: ${applied}ms")
    }

    fun simulateBtPlay() = headsetButtonNotifier.notifyButton("MEDIA_PLAY", source = "ui-simulate")

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
            try {
                textToSpeech.speak(text, lang)
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
            speechRecognizer.recognize(lang)
                .onSuccess { spoken ->
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
        private const val BLUETOOTH_REFRESH_MS = 15_000L
        private const val SERVICE_START_GRACE_MS = 1_000L
        private const val BT_TEST_READY_CUE = "BT test ready"
    }
}
