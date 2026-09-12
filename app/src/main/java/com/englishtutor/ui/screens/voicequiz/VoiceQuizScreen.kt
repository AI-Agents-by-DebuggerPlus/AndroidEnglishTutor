package com.englishtutor.ui.screens.voicequiz

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.session.VoiceQuizPhase
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceQuizScreen(
    onBack: () -> Unit,
    viewModel: VoiceQuizViewModel = hiltViewModel(),
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

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.voice_quiz_title))
                        BuildVersionSubtitle()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_quiz_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.voice_quiz_progress,
                    state.questionNumber.coerceAtMost(state.questionCount),
                    state.questionCount,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = phaseLabel(state.phase),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.promptRu.isNotBlank()) {
                Text(
                    text = state.promptRu,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(
                    R.string.voice_quiz_guessed_total,
                    state.totalGuessedWords,
                    state.remainingWords,
                    state.estimatedLevel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (state.lastSpoken.isNotBlank()) {
                Text(
                    text = stringResource(R.string.voice_quiz_last_spoken, state.lastSpoken),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = viewModel::onNext,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isActive && state.phase != VoiceQuizPhase.Listening,
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = null)
                Text(
                    text = stringResource(R.string.voice_quiz_next),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = viewModel::onPlay,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.isActive &&
                    state.phase != VoiceQuizPhase.Idle &&
                    state.phase != VoiceQuizPhase.CorrectFeedback &&
                    state.phase != VoiceQuizPhase.Completed,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.voice_quiz_play),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: VoiceQuizPhase): String {
    val res = when (phase) {
        VoiceQuizPhase.Idle -> R.string.voice_quiz_phase_idle
        VoiceQuizPhase.AskQuestion -> R.string.voice_quiz_phase_ask
        VoiceQuizPhase.Listening -> R.string.voice_quiz_phase_listening
        VoiceQuizPhase.CorrectFeedback -> R.string.voice_quiz_phase_correct
        VoiceQuizPhase.WrongFeedback -> R.string.voice_quiz_phase_wrong
        VoiceQuizPhase.Completed -> R.string.voice_quiz_phase_done
    }
    return stringResource(res)
}
