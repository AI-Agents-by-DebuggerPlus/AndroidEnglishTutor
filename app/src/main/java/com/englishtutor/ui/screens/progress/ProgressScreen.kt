package com.englishtutor.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.domain.model.TestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your Progress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Program completion", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${state.summary.completedLessons} / ${state.summary.totalLessons} lessons",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        LinearProgressIndicator(
                            progress = { state.summary.completionPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        )
                        Text(
                            text = "${state.summary.completionPercent}%",
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                Text("Completed lessons", style = MaterialTheme.typography.titleMedium)
            }

            if (state.summary.completedLessonIds.isEmpty()) {
                item {
                    Text("No lessons completed yet.")
                }
            } else {
                items(state.summary.completedLessonIds) { lessonId ->
                    Text("• $lessonId")
                }
            }

            item {
                Text("Test results", style = MaterialTheme.typography.titleMedium)
            }

            if (state.summary.testResults.isEmpty()) {
                item {
                    Text("No test results yet.")
                }
            } else {
                items(state.summary.testResults, key = { it.id }) { result ->
                    TestResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun TestResultCard(result: TestResult) {
    val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(result.completedAt))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = result.testType.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
            Text(text = "Level: ${result.level}")
            Text(text = "Score: ${result.score}%")
            Text(text = formattedDate, style = MaterialTheme.typography.bodySmall)
        }
    }
}
