package com.englishtutor.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.domain.model.Lesson
import com.englishtutor.ui.components.BuildVersionLabel
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLesson: (String) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVoiceTest: () -> Unit,
    onOpenLogs: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lessons (${state.level})")
                        BuildVersionSubtitle()
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::stopApp,
                        enabled = !state.isStopping,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.action_stop))
                    }
                    IconButton(onClick = onOpenVoiceTest) {
                        Icon(Icons.Default.Settings, contentDescription = "Окно тестов")
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Логи")
                    }
                    IconButton(onClick = onOpenProgress) {
                        Icon(Icons.Default.Info, contentDescription = "Progress")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                BuildVersionLabel()
            }
            item {
                Text(
                    text = state.bluetoothStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            item {
                Button(
                    onClick = onOpenVoiceTest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Окно тестов (речь + гарнитура)")
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenLogs,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Логи")
                    }
                    OutlinedButton(
                        onClick = onOpenProgress,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Прогресс")
                    }
                }
            }
            items(state.lessons, key = { it.id }) { lesson ->
                LessonCard(
                    lesson = lesson,
                    completed = lesson.id in state.completedLessonIds,
                    onClick = { onOpenLesson(lesson.id) },
                )
            }
        }
    }
}

@Composable
private fun LessonCard(
    lesson: Lesson,
    completed: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = lesson.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Level ${lesson.level}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (completed) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Completed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
