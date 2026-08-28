package com.englishtutor.ui.screens.placement

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.domain.model.PlacementQuestion
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacementTestScreen(
    onCompleted: () -> Unit,
    onOpenVoiceTest: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: PlacementTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(state.completed) {
        if (state.completed) onCompleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Placement Test")
                        BuildVersionSubtitle()
                    }
                },
                actions = {
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Логи")
                    }
                    IconButton(onClick = onOpenVoiceTest) {
                        Icon(Icons.Default.Settings, contentDescription = "Окно тестов")
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.resultLevel != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Your level: ${state.resultLevel}", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Score: ${state.resultScore}%")
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onCompleted) {
                        Text("Start learning")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onOpenVoiceTest, modifier = Modifier.fillMaxWidth()) {
                        Text("Окно тестов")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                        Text("Логи")
                    }
                }
            }

            else -> {
                val question = state.currentQuestion
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Button(
                        onClick = onOpenVoiceTest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Окно тестов (речь + гарнитура)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenLogs,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Логи")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { (state.currentIndex + 1f) / state.totalQuestions.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Question ${state.currentIndex + 1} of ${state.totalQuestions}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (question != null) {
                        QuestionContent(
                            question = question,
                            selectedIndex = state.selectedOptionIndex,
                            isRecording = state.isRecording,
                            lastRecognizedText = state.lastRecognizedText,
                            feedback = state.feedback,
                            onOptionSelected = viewModel::selectOption,
                            onRecord = viewModel::recordPronunciation,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = viewModel::submitAnswer,
                        enabled = state.canSubmit && !state.isRecording,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isLastQuestion) "Finish test" else "Next")
                    }

                    state.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(
    question: PlacementQuestion,
    selectedIndex: Int?,
    isRecording: Boolean,
    lastRecognizedText: String?,
    feedback: String?,
    onOptionSelected: (Int) -> Unit,
    onRecord: () -> Unit,
) {
    Text(text = question.question, style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(16.dp))

    when (question) {
        is PlacementQuestion.MultipleChoice -> {
            question.options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedIndex == index,
                            onClick = { onOptionSelected(index) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = { onOptionSelected(index) },
                    )
                    Text(text = option, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        is PlacementQuestion.Pronunciation -> {
            Text("Tap the button and say the phrase aloud.")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRecord, enabled = !isRecording) {
                Text(if (isRecording) "Listening..." else "Record pronunciation")
            }
            lastRecognizedText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Heard: $it")
            }
        }
    }

    feedback?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = it)
    }
}
