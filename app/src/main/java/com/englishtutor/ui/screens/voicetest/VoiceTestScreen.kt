package com.englishtutor.ui.screens.voicetest

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.bluetooth.ActiveMediaSessionRow
import com.englishtutor.bluetooth.ActiveBluetoothDevice
import com.englishtutor.bluetooth.ConnectedBluetoothDevice
import com.englishtutor.bluetooth.DiagnosticLevel
import com.englishtutor.bluetooth.HeadsetDiagnosticLine
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTestScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: VoiceTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onPermissionsResult(grants)
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    val tabs = listOf(
        stringResource(R.string.tests_tab_tts),
        stringResource(R.string.tests_tab_stt),
        stringResource(R.string.tests_tab_bt_play),
        stringResource(R.string.tests_tab_audio_route),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.tests_title))
                        BuildVersionSubtitle()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::closeApp,
                        enabled = !state.isClosing,
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = stringResource(R.string.action_close_app),
                        )
                    }
                    when (state.selectedTab) {
                        2 -> {
                            IconButton(onClick = viewModel::resetBtPlayCounter) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Сбросить счётчик",
                                )
                            }
                        }
                        3 -> {
                            IconButton(onClick = viewModel::refreshAudioRoute) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.tests_audio_route_refresh),
                                )
                            }
                        }
                    }
                    IconButton(onClick = onOpenLogs) {
                        Text("Логи", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Сборка: ${state.versionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            BluetoothDevicesSection(
                permissionGranted = state.bluetoothPermissionGranted,
                connectedDevices = state.connectedBluetoothDevices,
                activeDevice = state.activeBluetoothDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )

            ScrollableTabRow(selectedTabIndex = state.selectedTab, edgePadding = 8.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) },
                    )
                }
            }

            when (state.selectedTab) {
                0 -> TtsTestSection(
                    speakText = state.speakText,
                    languageCode = state.languageCode,
                    isSpeaking = state.isSpeaking,
                    isBusy = state.isBusy,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    onSpeakTextChanged = viewModel::onSpeakTextChanged,
                    onLanguageChanged = viewModel::onLanguageChanged,
                    onSpeak = viewModel::speak,
                )
                1 -> SttTestSection(
                    languageCode = state.languageCode,
                    isRecording = state.isRecording,
                    isBusy = state.isBusy,
                    recognizedText = state.recognizedText,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    onLanguageChanged = viewModel::onLanguageChanged,
                    onRecognize = viewModel::recognize,
                    onSpeakThenRecognize = viewModel::speakThenRecognize,
                )
                2 -> BtPlayTestSection(
                    pressCount = state.btPressCount,
                    nextCount = state.btNextCount,
                    stopCount = state.btStopCount,
                    quadCount = state.btQuadCount,
                    doubleNextCount = state.btDoubleNextCount,
                    pendingBurstCount = state.btPendingBurstCount,
                    awaitingSecondDouble = state.btAwaitingSecondDouble,
                    lastEventLabel = state.btLastEventLabel,
                    lastEventAt = state.btLastEventAt,
                    nativeCaptureOn = state.nativeCaptureOn,
                    taskerMayConflict = state.taskerMayConflict,
                    debounceEnabled = state.debounceEnabled,
                    debounceIntervalText = state.debounceIntervalText,
                    nextDoubleTapText = state.nextDoubleTapText,
                    doubleNextIntervalText = state.doubleNextIntervalText,
                    eventLog = state.btEventLog,
                    onDebounceEnabledChange = viewModel::setDebounceEnabled,
                    onDebounceIntervalTextChange = viewModel::onDebounceIntervalTextChanged,
                    onDebounceIntervalCommit = viewModel::commitDebounceInterval,
                    onNextDoubleTapTextChange = viewModel::onNextDoubleTapTextChanged,
                    onNextDoubleTapCommit = viewModel::commitNextDoubleTapInterval,
                    onDoubleNextIntervalTextChange = viewModel::onDoubleNextIntervalTextChanged,
                    onDoubleNextIntervalCommit = viewModel::commitDoubleNextInterval,
                    onSimulate = viewModel::simulateBtPlay,
                    onSimulateQuad = { viewModel.simulateBtPlayBurst(4) },
                    onResetCounters = viewModel::resetBtPlayCounter,
                    onReassert = { viewModel.reassertBtPlayCapture(speakCue = true) },
                )
                else -> AudioRouteTestSection(
                    languageCode = state.languageCode,
                    isRecording = state.isRecording,
                    isSpeaking = state.isSpeaking,
                    isBusy = state.isBusy,
                    recognizedText = state.recognizedText,
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage,
                    audioRoute = state.audioRoute,
                    onLanguageChanged = viewModel::onLanguageChanged,
                    onPlay = viewModel::recognizeAndSpeak,
                    onRefreshRoute = viewModel::refreshAudioRoute,
                )
            }

            Button(
                onClick = viewModel::closeApp,
                enabled = !state.isClosing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.action_close_app))
            }

            HeadsetDiagnosticsSection(
                summary = state.diagnosticsSummary,
                mediaButtonPathReady = state.mediaButtonPathReady,
                lines = state.diagnosticLines,
                activeSessions = state.activeMediaSessions,
                notificationAccessEnabled = state.notificationAccessEnabled,
                onRefresh = viewModel::refreshDiagnostics,
                onOpenNotificationAccess = viewModel::openNotificationAccessSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BluetoothDevicesSection(
    permissionGranted: Boolean,
    connectedDevices: List<ConnectedBluetoothDevice>,
    activeDevice: ActiveBluetoothDevice?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.bt_devices_section_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (!permissionGranted) {
                Text(
                    text = stringResource(R.string.bt_devices_no_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(
                text = stringResource(R.string.bt_devices_connected_title),
                style = MaterialTheme.typography.labelLarge,
            )
            if (connectedDevices.isEmpty()) {
                Text(
                    text = stringResource(R.string.bt_devices_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                connectedDevices.forEach { device ->
                    Text(
                        text = device.displayLine(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
            Text(
                text = stringResource(R.string.bt_devices_active_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = activeDevice?.displayLine()
                    ?: stringResource(R.string.bt_devices_active_none),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = if (activeDevice != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun TtsTestSection(
    speakText: String,
    languageCode: String,
    isSpeaking: Boolean,
    isBusy: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onSpeakTextChanged: (String) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onSpeak: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = speakText,
            onValueChange = onSpeakTextChanged,
            label = { Text("Текст для озвучки") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedTextField(
            value = languageCode,
            onValueChange = onLanguageChanged,
            label = { Text("Код языка (en-US / ru-RU)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(
            onClick = onSpeak,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSpeaking) "Идёт озвучка…" else "Прослушать")
        }
        statusMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.secondary)
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SttTestSection(
    languageCode: String,
    isRecording: Boolean,
    isBusy: Boolean,
    recognizedText: String?,
    statusMessage: String?,
    errorMessage: String?,
    onLanguageChanged: (String) -> Unit,
    onRecognize: () -> Unit,
    onSpeakThenRecognize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = languageCode,
            onValueChange = onLanguageChanged,
            label = { Text("Код языка (en-US / ru-RU)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            text = statusMessage ?: if (isRecording) "Слушаю…" else "Готов к записи",
            style = MaterialTheme.typography.titleMedium,
            color = if (isRecording) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        Text(
            text = recognizedText?.ifBlank { "—" } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRecognize,
                enabled = !isBusy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Записать")
            }
            OutlinedButton(
                onClick = { /* cancel not wired */ },
                enabled = isRecording,
                modifier = Modifier.weight(1f),
            ) {
                Text("Отмена")
            }
        }
        OutlinedButton(
            onClick = onSpeakThenRecognize,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Прослушать → записать")
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BtPlayCounter(
    value: Int,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            fontSize = 36.sp,
            style = MaterialTheme.typography.displayLarge,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun BtPlayTestSection(
    pressCount: Int,
    nextCount: Int,
    stopCount: Int,
    quadCount: Int,
    doubleNextCount: Int,
    pendingBurstCount: Int,
    awaitingSecondDouble: Boolean,
    lastEventLabel: String,
    lastEventAt: String,
    nativeCaptureOn: Boolean,
    taskerMayConflict: Boolean,
    debounceEnabled: Boolean,
    debounceIntervalText: String,
    nextDoubleTapText: String,
    doubleNextIntervalText: String,
    eventLog: List<String>,
    onDebounceEnabledChange: (Boolean) -> Unit,
    onDebounceIntervalTextChange: (String) -> Unit,
    onDebounceIntervalCommit: () -> Unit,
    onNextDoubleTapTextChange: (String) -> Unit,
    onNextDoubleTapCommit: () -> Unit,
    onDoubleNextIntervalTextChange: (String) -> Unit,
    onDoubleNextIntervalCommit: () -> Unit,
    onSimulate: () -> Unit,
    onSimulateQuad: () -> Unit,
    onResetCounters: () -> Unit,
    onReassert: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.bt_play_test_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (nativeCaptureOn) {
                stringResource(R.string.bt_play_test_capture_on)
            } else {
                stringResource(R.string.bt_play_test_capture_off)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (nativeCaptureOn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (taskerMayConflict) {
            Text(
                text = stringResource(R.string.bt_play_tasker_conflict),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.bt_play_test_debounce_enable),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = debounceEnabled,
                onCheckedChange = onDebounceEnabledChange,
            )
        }
        OutlinedTextField(
            value = debounceIntervalText,
            onValueChange = onDebounceIntervalTextChange,
            enabled = debounceEnabled,
            label = { Text(stringResource(R.string.bt_play_test_debounce_interval)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onDebounceIntervalCommit()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = nextDoubleTapText,
            onValueChange = onNextDoubleTapTextChange,
            label = { Text(stringResource(R.string.bt_play_test_next_interval)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onNextDoubleTapCommit()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = doubleNextIntervalText,
            onValueChange = onDoubleNextIntervalTextChange,
            label = { Text(stringResource(R.string.bt_play_test_double_next_interval)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onDoubleNextIntervalCommit()
                    focusManager.clearFocus()
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BtPlayCounter(
                value = pressCount,
                label = stringResource(R.string.bt_play_test_count_label),
                color = MaterialTheme.colorScheme.primary,
            )
            BtPlayCounter(
                value = nextCount,
                label = stringResource(R.string.bt_play_test_next_count_label),
                color = MaterialTheme.colorScheme.secondary,
            )
            BtPlayCounter(
                value = stopCount,
                label = stringResource(R.string.bt_play_test_stop_count_label),
                color = MaterialTheme.colorScheme.tertiary,
            )
            BtPlayCounter(
                value = quadCount,
                label = stringResource(R.string.bt_play_test_quad_count_label),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BtPlayCounter(
                value = doubleNextCount,
                label = stringResource(R.string.bt_play_test_double_next_count_label),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (pendingBurstCount > 0) {
            Text(
                text = stringResource(R.string.bt_play_test_pending_burst, pendingBurstCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(
                R.string.bt_play_test_last_event,
                lastEventLabel.ifBlank { "—" },
                lastEventAt.ifBlank { "—" },
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onReassert,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bt_play_test_reassert))
        }
        OutlinedButton(
            onClick = onSimulate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(R.string.bt_play_test_simulate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedButton(
            onClick = onSimulateQuad,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.bt_play_test_simulate_quad))
        }
        OutlinedButton(
            onClick = onResetCounters,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text(
                text = stringResource(R.string.bt_play_test_reset_counters),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.bt_play_test_log_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        if (eventLog.isEmpty()) {
            Text(
                text = stringResource(R.string.bt_play_test_log_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            eventLog.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AudioRouteTestSection(
    languageCode: String,
    isRecording: Boolean,
    isSpeaking: Boolean,
    isBusy: Boolean,
    recognizedText: String?,
    statusMessage: String?,
    errorMessage: String?,
    audioRoute: AudioRouteUiState,
    onLanguageChanged: (String) -> Unit,
    onPlay: () -> Unit,
    onRefreshRoute: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tests_audio_route_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = languageCode,
            onValueChange = onLanguageChanged,
            label = { Text("Код языка (en-US / ru-RU)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Text(
            text = statusMessage ?: if (isRecording) {
                "Слушаю…"
            } else if (isSpeaking) {
                "Озвучка…"
            } else {
                "Готов"
            },
            style = MaterialTheme.typography.titleMedium,
            color = when {
                isRecording || isSpeaking -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                R.string.tests_audio_recognized,
                recognizedText?.ifBlank { "—" } ?: "—",
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onPlay,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(R.string.tests_audio_route_play),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        OutlinedButton(
            onClick = onRefreshRoute,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.tests_audio_route_refresh))
        }
        HorizontalDivider()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (audioRoute.a2dpOn) {
                        stringResource(R.string.tests_audio_a2dp_on)
                    } else {
                        stringResource(R.string.tests_audio_a2dp_off)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (audioRoute.a2dpOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = if (audioRoute.readyForMediaTts) {
                        stringResource(R.string.tests_audio_ready)
                    } else {
                        stringResource(R.string.tests_audio_not_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (audioRoute.readyForMediaTts) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Text(
                    text = stringResource(R.string.tests_audio_playback_device, audioRoute.playbackDevice),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = stringResource(R.string.tests_audio_mode, audioRoute.modeLabel),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = stringResource(
                        R.string.tests_audio_sco,
                        if (audioRoute.scoOn) "on" else "off",
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = stringResource(R.string.tests_audio_comm, audioRoute.communicationDevice),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = stringResource(R.string.tests_audio_a2dp_out, audioRoute.a2dpOutputs),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                Text(
                    text = stringResource(R.string.tests_audio_sco_out, audioRoute.scoOutputs),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun HeadsetDiagnosticsSection(
    summary: String,
    mediaButtonPathReady: Boolean,
    lines: List<HeadsetDiagnosticLine>,
    activeSessions: List<ActiveMediaSessionRow>,
    notificationAccessEnabled: Boolean,
    onRefresh: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.headset_diagnostics_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.headset_diagnostics_refresh))
                }
            }
            Text(
                text = summary.ifBlank { stringResource(R.string.headset_diagnostics_pending) },
                style = MaterialTheme.typography.bodyMedium,
                color = if (mediaButtonPathReady) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            lines.forEach { line ->
                DiagnosticLineRow(line)
            }
            if (!notificationAccessEnabled) {
                OutlinedButton(
                    onClick = onOpenNotificationAccess,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.headset_diagnostics_notification_access))
                }
            } else if (activeSessions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.headset_diagnostics_sessions_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                activeSessions.take(6).forEach { row ->
                    Text(
                        text = row.displayLine(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = when {
                            row.receivesButton && row.isSelf -> MaterialTheme.colorScheme.primary
                            row.receivesButton -> MaterialTheme.colorScheme.error
                            row.isKnownCompetitor -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLineRow(line: HeadsetDiagnosticLine) {
    val marker = when (line.level) {
        DiagnosticLevel.OK -> "✓"
        DiagnosticLevel.WARN -> "!"
        DiagnosticLevel.FAIL -> "✗"
    }
    val color = when (line.level) {
        DiagnosticLevel.OK -> MaterialTheme.colorScheme.primary
        DiagnosticLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DiagnosticLevel.FAIL -> MaterialTheme.colorScheme.error
    }
    Text(
        text = "$marker ${line.label}: ${line.detail}",
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}
