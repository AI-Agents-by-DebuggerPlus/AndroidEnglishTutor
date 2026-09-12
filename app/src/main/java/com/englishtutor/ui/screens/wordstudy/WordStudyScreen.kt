package com.englishtutor.ui.screens.wordstudy

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.data.voice.StudyFontPreferences
import com.englishtutor.session.WordStudyPhase
import com.englishtutor.ui.components.BuildVersionSubtitle

private val StudyBlack = Color.Black
private val StudyWhite = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordStudyScreen(
    onBack: () -> Unit,
    viewModel: WordStudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        onDispose { viewModel.onScreenHidden() }
    }

    StudySystemBars(fullscreen = fullscreen)

    BackHandler(enabled = fullscreen) {
        fullscreen = false
    }

    if (fullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(StudyBlack),
            contentAlignment = Alignment.Center,
        ) {
            WordStudyContent(
                english = state.study.english,
                russian = state.study.russian,
                enSp = state.enSp,
                ruSp = state.ruSp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            )
            IconButton(
                onClick = { fullscreen = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(
                    Icons.Default.FullscreenExit,
                    contentDescription = stringResource(R.string.word_study_exit_fullscreen),
                    tint = StudyWhite,
                )
            }
        }
        return
    }

    Scaffold(
        containerColor = StudyBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudyBlack,
                    titleContentColor = StudyWhite,
                    navigationIconContentColor = StudyWhite,
                    actionIconContentColor = StudyWhite,
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.word_study_title), color = StudyWhite)
                        BuildVersionSubtitle()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(
                            Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.word_study_fullscreen),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(StudyBlack)
                .padding(padding)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.word_study_summary,
                    state.study.totalGuessedWords,
                    state.study.unguessedWords,
                    state.study.lessonCount,
                    state.study.estimatedLevel,
                ),
                color = StudyWhite.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = state.study.statusMessage,
                color = StudyWhite.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WordStudyContent(
                    english = state.study.english,
                    russian = state.study.russian,
                    enSp = state.enSp,
                    ruSp = state.ruSp,
                )
            }

            Text(
                text = stringResource(R.string.word_study_font_en, state.enSp.toInt()),
                color = StudyWhite,
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = state.enSp,
                onValueChange = viewModel::setEnSp,
                valueRange = StudyFontPreferences.MIN_SP..StudyFontPreferences.MAX_SP,
            )
            Text(
                text = stringResource(R.string.word_study_font_ru, state.ruSp.toInt()),
                color = StudyWhite,
                style = MaterialTheme.typography.bodySmall,
            )
            Slider(
                value = state.ruSp,
                onValueChange = viewModel::setRuSp,
                valueRange = StudyFontPreferences.MIN_SP..StudyFontPreferences.MAX_SP,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = viewModel::onNext,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                    Text(
                        text = stringResource(R.string.word_study_next),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = viewModel::onPlay,
                    modifier = Modifier.weight(1f),
                    enabled = state.study.phase == WordStudyPhase.ShowingWord,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(
                        text = stringResource(R.string.word_study_play),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WordStudyContent(
    english: String,
    russian: String,
    enSp: Float,
    ruSp: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (english.isNotBlank()) {
            Text(
                text = english,
                color = StudyWhite,
                fontSize = enSp.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = russian,
                color = StudyWhite,
                fontSize = ruSp.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = stringResource(R.string.word_study_waiting),
                color = StudyWhite.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StudySystemBars(fullscreen: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(fullscreen) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (controller != null) {
            if (fullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
