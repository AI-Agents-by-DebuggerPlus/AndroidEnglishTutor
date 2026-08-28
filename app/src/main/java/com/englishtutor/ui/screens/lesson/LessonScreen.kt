package com.englishtutor.ui.screens.lesson

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.session.LessonSessionService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lessonId: String,
    onBack: () -> Unit,
    viewModel: LessonViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val mic = grants[Manifest.permission.RECORD_AUDIO] == true
        viewModel.onAudioPermissionResult(mic)
    }

    LaunchedEffect(lessonId) {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
        viewModel.loadLesson(lessonId)
    }

    DisposableEffect(lessonId) {
        val intent = Intent(context, LessonSessionService::class.java).apply {
            action = LessonSessionService.ACTION_START
            putExtra(LessonSessionService.EXTRA_LESSON_ID, lessonId)
        }
        context.startForegroundService(intent)
        onDispose {
            context.startService(
                Intent(context, LessonSessionService::class.java).apply {
                    action = LessonSessionService.ACTION_STOP
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.lessonTitle.ifBlank { "Lesson" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Гарнитура: Play = слушать/записать · Next = пропуск · Previous = сначала",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = state.lessonContent, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Упражнение ${state.phraseProgress}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(text = state.currentPhrase, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Фаза: ${state.phaseLabel}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = viewModel::onPlayPause,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                state.isRecording -> "Listening..."
                                state.isSpeaking -> "Playing..."
                                state.phraseSpoken -> "Record (Play)"
                                else -> "Listen (Play)"
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::onPrevious,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Previous — с начала")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::onNext,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Next — пропустить")
                    }

                    state.lastRecognizedText?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Heard: $it")
                    }

                    state.pronunciationScore?.let { score ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Match score: ${(score * 100).toInt()}%")
                    }

                    state.statusMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it)
                    }

                    if (state.isCompleted) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Урок завершён",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (!state.audioFocusHeld && state.isSessionActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Audio focus временно потерян — сессия продолжается",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
