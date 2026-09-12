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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.englishtutor.session.VoiceQuizPhase
import com.englishtutor.ui.components.BuildVersionLabel
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLesson: (String) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVoiceTest: () -> Unit,
    onOpenVoiceQuiz: () -> Unit,
    onOpenQuizStats: () -> Unit,
    onOpenVoicePicker: () -> Unit,
    onOpenWordStudy: () -> Unit,
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
        bottomBar = {
            Button(
                onClick = viewModel::stopApp,
                enabled = !state.isStopping,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text(
                    text = stringResource(R.string.action_power_off),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.voice_quiz_home_card_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.voice_quiz_home_card_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.quiz.promptRu.isNotBlank()) {
                            Text(
                                text = state.quiz.promptRu,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Text(
                            text = state.quiz.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                text = stringResource(
                    R.string.voice_quiz_guessed_total,
                    state.quiz.totalGuessedWords,
                    state.quiz.remainingWords,
                    state.quiz.estimatedLevel,
                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = viewModel::onQuizNext,
                                modifier = Modifier.weight(1f),
                                enabled = state.quiz.phase != VoiceQuizPhase.Listening,
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null)
                                Text(
                                    text = "Next",
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::onQuizPlay,
                                modifier = Modifier.weight(1f),
                                enabled = state.quiz.phase == VoiceQuizPhase.AskQuestion ||
                                    state.quiz.phase == VoiceQuizPhase.WrongFeedback ||
                                    state.quiz.phase == VoiceQuizPhase.Listening,
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text(
                                    text = "Play",
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
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
                Button(
                    onClick = onOpenWordStudy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.word_study_home_button))
                }
            }
            item {
                Button(
                    onClick = onOpenVoiceQuiz,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.voice_quiz_home_button))
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenQuizStats,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.quiz_stats_home_button))
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenVoicePicker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.voice_picker_home_button))
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
