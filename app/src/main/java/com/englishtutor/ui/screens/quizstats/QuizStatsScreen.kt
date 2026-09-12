package com.englishtutor.ui.screens.quizstats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.data.stats.VoiceQuizSessionStats
import com.englishtutor.ui.components.BuildVersionSubtitle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizStatsScreen(
    onBack: () -> Unit,
    viewModel: QuizStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.quiz_stats_title))
                        BuildVersionSubtitle()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.busy) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.quiz_stats_refresh))
                    }
                    IconButton(onClick = viewModel::clearLocal, enabled = !state.busy) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.quiz_stats_clear))
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
                Text(
                    text = stringResource(
                        R.string.quiz_stats_summary,
                        state.stats.totalSessions,
                        state.stats.completedSessions,
                        state.stats.totalAttempts,
                        state.stats.totalCorrectAnswers,
                        state.stats.totalGuessedWords,
                        state.stats.estimatedLevel,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.quiz_stats_file, state.filePath),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    onClick = viewModel::uploadToServer,
                    enabled = !state.busy && state.stats.sessions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy && state.busyAction == QuizStatsBusy.Upload) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.quiz_stats_upload),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::downloadFromServer,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.busy && state.busyAction == QuizStatsBusy.Download) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                    Text(
                        text = stringResource(R.string.quiz_stats_download),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            state.statusMessage?.let { msg ->
                item {
                    Text(text = msg, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.errorMessage?.let { err ->
                item {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (state.stats.sessions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.quiz_stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.stats.sessions, key = { it.id }) { session ->
                    SessionCard(session)
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: VoiceQuizSessionStats) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.quiz_stats_session_title,
                    fmt.format(Date(session.startedAtMs)),
                    if (session.completed) stringResource(R.string.quiz_stats_completed)
                    else stringResource(R.string.quiz_stats_incomplete),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    R.string.quiz_stats_session_counts,
                    session.correctAnswers,
                    session.wrongAttempts,
                    session.questionsTotal,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            session.attempts.takeLast(6).forEach { attempt ->
                val mark = if (attempt.correct) "✓" else "✗"
                Text(
                    text = "$mark ${attempt.stimulusRu}: «${attempt.spoken.ifBlank { "—" }}»",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}
