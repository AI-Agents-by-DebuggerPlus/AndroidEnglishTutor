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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.session.HeadsetTestService
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTestScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: VoiceTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.onMicPermission(grants[Manifest.permission.RECORD_AUDIO] == true)
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(state.selectedTab) {
        if (state.selectedTab == 2) {
            HeadsetTestService.start(context)
        } else {
            HeadsetTestService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            HeadsetTestService.stop(context)
        }
    }

    val tabs = listOf(
        stringResource(R.string.tests_tab_tts),
        stringResource(R.string.tests_tab_stt),
        stringResource(R.string.tests_tab_bt_play),
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
                    when (state.selectedTab) {
                        2 -> {
                            IconButton(onClick = viewModel::resetBtPlayCounter) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Сбросить счётчик",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "Сборка: ${state.versionLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                else -> BtPlayTestSection(
                    pressCount = state.btPressCount,
                    lastEventLabel = state.btLastEventLabel,
                    lastEventAt = state.btLastEventAt,
                    nativeCaptureOn = state.headsetActive,
                    eventLog = state.btEventLog,
                    onSimulate = viewModel::simulateBtPlay,
                )
            }
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
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
            .fillMaxSize()
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
private fun BtPlayTestSection(
    pressCount: Int,
    lastEventLabel: String,
    lastEventAt: String,
    nativeCaptureOn: Boolean,
    eventLog: List<String>,
    onSimulate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Text(
            text = pressCount.toString(),
            fontSize = 72.sp,
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.bt_play_test_count_label),
            style = MaterialTheme.typography.titleMedium,
        )
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
            onClick = onSimulate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(
                text = stringResource(R.string.bt_play_test_simulate),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.bt_play_test_log_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (eventLog.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.bt_play_test_log_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(eventLog) { line ->
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
}
