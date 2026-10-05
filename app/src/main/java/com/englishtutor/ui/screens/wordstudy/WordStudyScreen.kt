package com.englishtutor.ui.screens.wordstudy

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.data.topics.TopicInfo
import com.englishtutor.ui.flashcards.FlashcardDisplaySettings
import com.englishtutor.ui.flashcards.rememberFlashcardResolvedLayout
import kotlinx.coroutines.delay

private val HintMuted = Color(0xFF8AA8A6)
private val Cream = Color(0xFFE8F4F3)
private val FlashcardChromeHeight = 48.dp
private val CountdownBarHeight = 4.dp

@Composable
fun WordStudyScreen(
    onBack: () -> Unit,
    onOpenVoicePicker: () -> Unit = {},
    viewModel: WordStudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var showSettings by remember { mutableStateOf(false) }
    var showTopics by remember { mutableStateOf(false) }
    var blanked by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.onScreenVisible()
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prevOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            viewModel.onScreenHidden()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (prevOrientation != null) {
                activity.requestedOrientation = prevOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val display = state.display
    val english = state.study.english
    val russian = state.study.russian
    val hasCard = english.isNotBlank()

    FlashcardImmersiveEffect(
        active = !showSettings && !showTopics,
        systemBarColor = if (blanked) Color.Black else display.backgroundColor,
    )

    fun leaveStudy() {
        viewModel.endStudySession()
        onBack()
    }

    BackHandler {
        when {
            showSettings -> showSettings = false
            showTopics -> showTopics = false
            else -> leaveStudy()
        }
    }

    LaunchedEffect(blanked) {
        if (blanked) {
            showSettings = false
            showTopics = false
        }
    }

    // Any show/replay (including Play while blank) un-blanks via displayEpoch.
    LaunchedEffect(state.study.displayEpoch) {
        if (state.study.displayEpoch > 0L) {
            blanked = false
        }
    }

    var secondsLeft by remember { mutableIntStateOf(display.blankAfterSeconds) }
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(
        state.study.displayEpoch,
        blanked,
        showSettings,
        showTopics,
        display.blankAfterSeconds,
        hasCard,
    ) {
        if (!hasCard || blanked || showSettings || showTopics) {
            progress = if (blanked) 0f else 1f
            return@LaunchedEffect
        }
        val total = display.blankAfterSeconds.coerceIn(5, 300)
        val totalMs = total * 1_000L
        val started = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - started
            if (elapsed >= totalMs) break
            val leftMs = totalMs - elapsed
            secondsLeft = ((leftMs + 999) / 1_000).toInt().coerceAtLeast(0)
            progress = (leftMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            delay(50)
        }
        secondsLeft = 0
        progress = 0f
        blanked = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (blanked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { blanked = false },
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(display.backgroundColor),
            ) {
                val enText = if (hasCard) {
                    english
                } else {
                    stringResource(R.string.word_study_waiting)
                }
                val ruText = russian.takeIf { hasCard && it.isNotBlank() }
                val layout = rememberFlashcardResolvedLayout(
                    settings = display,
                    english = enText,
                    russian = ruText,
                    contentWidth = maxWidth - 48.dp,
                    contentHeight = maxHeight,
                    chromeHeight = FlashcardChromeHeight + 8.dp,
                )
                Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable {
                                // Tap card area = Play (start or replay).
                                viewModel.onPlay()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(layout.englishTopDp.dp))
                        Text(
                            text = enText,
                            color = if (hasCard) display.englishColor else HintMuted,
                            fontSize = layout.englishSp.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = (layout.englishSp * 1.15f).sp,
                        )
                        if (ruText != null) {
                            Spacer(Modifier.height(layout.enRuGapDp.dp))
                            Text(
                                text = ruText,
                                color = display.russianColor,
                                fontSize = layout.russianSp.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = (layout.russianSp * 1.2f).sp,
                            )
                        }
                    }
                    FlashcardBottomChrome(
                        countdownProgress = if (hasCard) progress else 0f,
                        secondsLeft = if (hasCard) secondsLeft else null,
                        progressLabel = buildProgressLabel(state),
                        playEnabled = true,
                        onNext = { viewModel.onNext() },
                        onPlay = { viewModel.onPlay() },
                        onTopics = { showTopics = true },
                        onVoices = onOpenVoicePicker,
                        onSettings = { showSettings = true },
                        onExit = { leaveStudy() },
                    )
                }
            }
        }
    }

    if (showTopics) {
        TopicPickerDialog(
            topics = state.study.topics,
            selectedId = state.study.topicId,
            onSelect = {
                showTopics = false
                viewModel.selectTopic(it)
            },
            onDismiss = { showTopics = false },
        )
    }

    if (showSettings) {
        FlashcardSettingsDialog(
            current = display,
            previewEn = english.ifBlank { "to achieve" },
            previewRu = russian.ifBlank { "достигать, добиваться" },
            summary = stringResource(
                R.string.word_study_summary,
                state.study.totalGuessedWords,
                state.study.studiedCount,
                state.study.wordCount,
                state.study.estimatedLevel,
            ),
            status = listOfNotNull(
                state.study.topicTitle.takeIf { it.isNotBlank() },
                state.study.stage.labelRu,
                state.study.statusMessage.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            onChange = viewModel::updateDisplay,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun buildProgressLabel(state: WordStudyUiState): String {
    val study = state.study
    return if (study.english.isNotBlank()) {
        stringResource(
            R.string.word_study_card_progress_topic,
            study.topicTitle.ifBlank { "—" },
            study.stage.labelRu,
            study.wordIndex + 1,
            study.wordCount.coerceAtLeast(1),
        )
    } else {
        study.topicTitle.ifBlank { study.estimatedLevel }
    }
}

@Composable
private fun FlashcardBottomChrome(
    countdownProgress: Float,
    secondsLeft: Int?,
    progressLabel: String,
    playEnabled: Boolean,
    onNext: () -> Unit,
    onPlay: () -> Unit,
    onTopics: () -> Unit,
    onVoices: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlashcardChromeHeight)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearProgressIndicator(
            progress = { countdownProgress.coerceIn(0f, 1f) },
            modifier = Modifier
                .padding(start = 8.dp)
                .width(72.dp)
                .height(CountdownBarHeight),
            color = HintMuted,
            trackColor = HintMuted.copy(alpha = 0.25f),
        )
        Text(
            text = secondsLeft?.toString() ?: "—",
            color = HintMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            text = progressLabel,
            color = HintMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
            maxLines = 1,
        )
        FlashcardChromeIcon(onClick = onNext) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.word_study_next),
                tint = HintMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        FlashcardChromeIcon(onClick = onPlay, enabled = playEnabled) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.word_study_play),
                tint = if (playEnabled) HintMuted else HintMuted.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp),
            )
        }
        FlashcardChromeIcon(onClick = onTopics) {
            Icon(
                Icons.Default.Topic,
                contentDescription = stringResource(R.string.word_study_topics),
                tint = HintMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        FlashcardChromeIcon(onClick = onVoices) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = stringResource(R.string.word_study_voices),
                tint = HintMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        FlashcardChromeIcon(onClick = onSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.word_study_settings),
                tint = HintMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        FlashcardChromeIcon(onClick = onExit) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.action_back),
                tint = HintMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TopicPickerDialog(
    topics: List<TopicInfo>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF123D45),
        title = {
            Text(stringResource(R.string.word_study_topics_title), color = Cream)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                topics.forEach { topic ->
                    val selected = topic.id == selectedId
                    Text(
                        text = "${topic.titleRu} (${topic.wordCount})",
                        color = if (selected) Cream else HintMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(topic.id) }
                            .padding(vertical = 10.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(
                    text = stringResource(R.string.word_study_triple_play_hint),
                    color = HintMuted.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.word_study_settings_done), color = Cream)
            }
        },
    )
}

@Composable
private fun FlashcardChromeIcon(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
    ) {
        content()
    }
}

@Composable
private fun FlashcardImmersiveEffect(active: Boolean, systemBarColor: Color) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    DisposableEffect(active, systemBarColor) {
        if (!active) {
            return@DisposableEffect onDispose { }
        }
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val prevStatus = window.statusBarColor
        val prevNav = window.navigationBarColor
        val bar = systemBarColor.toArgb()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = bar
        @Suppress("DEPRECATION")
        window.navigationBarColor = bar
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            @Suppress("DEPRECATION")
            window.statusBarColor = prevStatus
            @Suppress("DEPRECATION")
            window.navigationBarColor = prevNav
        }
    }
}

@Composable
private fun FlashcardSettingsDialog(
    current: FlashcardDisplaySettings,
    previewEn: String,
    previewRu: String,
    summary: String,
    status: String,
    onChange: (FlashcardDisplaySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF123D45),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.word_study_settings_title),
                        color = Cream,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    FlashcardChromeIcon(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.word_study_settings_close),
                            tint = Cream,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(summary, color = HintMuted, style = MaterialTheme.typography.bodySmall)
                    if (status.isNotBlank()) {
                        Text(
                            status,
                            color = HintMuted.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(draft.backgroundColor, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        val previewLayout = rememberFlashcardResolvedLayout(
                            settings = draft,
                            english = previewEn,
                            russian = previewRu,
                            contentWidth = maxWidth,
                            contentHeight = maxHeight,
                            chromeHeight = 0.dp,
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(previewLayout.englishTopDp.dp))
                            Text(
                                previewEn,
                                color = draft.englishColor,
                                fontSize = previewLayout.englishSp.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(previewLayout.enRuGapDp.dp))
                            Text(
                                previewRu,
                                color = draft.russianColor,
                                fontSize = previewLayout.russianSp.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.word_study_font_en_label), color = Cream)
                    Slider(
                        value = draft.englishSp.toFloat(),
                        onValueChange = {
                            val en = it.toInt()
                            draft = draft.copy(
                                englishSp = en,
                                russianSp = draft.targetRussianSp(en),
                            )
                            onChange(draft)
                        },
                        valueRange = 18f..72f,
                        steps = 26,
                    )
                    Text(
                        stringResource(
                            R.string.word_study_ru_smaller,
                            draft.russianSmallerPercent,
                            draft.russianSp,
                        ),
                        color = Cream,
                    )
                    Slider(
                        value = draft.russianSmallerPercent.toFloat(),
                        onValueChange = {
                            val pct = it.toInt()
                            draft = draft.copy(
                                russianSmallerPercent = pct,
                                russianSp = draft.copy(russianSmallerPercent = pct)
                                    .targetRussianSp(draft.englishSp),
                            )
                            onChange(draft)
                        },
                        valueRange = 10f..55f,
                        steps = 44,
                    )
                    Text(stringResource(R.string.word_study_font_ru_label), color = Cream)
                    Slider(
                        value = draft.russianSp.toFloat(),
                        onValueChange = {
                            val ru = it.toInt().coerceAtMost(draft.englishSp - 1)
                            draft = draft.copy(russianSp = ru.coerceAtLeast(10))
                            onChange(draft)
                        },
                        valueRange = 14f..48f,
                        steps = 16,
                    )
                    Text(
                        stringResource(R.string.word_study_en_top_pad, draft.englishTopPaddingDp),
                        color = Cream,
                    )
                    Slider(
                        value = draft.englishTopPaddingDp.toFloat(),
                        onValueChange = {
                            draft = draft.copy(englishTopPaddingDp = it.toInt())
                            onChange(draft)
                        },
                        valueRange = 0f..120f,
                        steps = 24,
                    )
                    Text(
                        stringResource(R.string.word_study_en_ru_gap, draft.enRuGapDp),
                        color = Cream,
                    )
                    Slider(
                        value = draft.enRuGapDp.toFloat(),
                        onValueChange = {
                            draft = draft.copy(enRuGapDp = it.toInt())
                            onChange(draft)
                        },
                        valueRange = 0f..80f,
                        steps = 16,
                    )
                    Text(
                        stringResource(R.string.word_study_blank_after, draft.blankAfterSeconds),
                        color = Cream,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Slider(
                        value = draft.blankAfterSeconds.toFloat(),
                        onValueChange = {
                            draft = draft.copy(blankAfterSeconds = it.toInt())
                            onChange(draft)
                        },
                        valueRange = 5f..300f,
                        steps = 58,
                    )
                    Text(
                        stringResource(R.string.word_study_color_en),
                        color = Cream,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ColorPresetRow(
                        presets = FlashcardDisplaySettings.englishColorPresets,
                        selected = draft.englishColorArgb,
                        onSelect = {
                            draft = draft.copy(englishColorArgb = it)
                            onChange(draft)
                        },
                    )
                    Text(stringResource(R.string.word_study_color_ru), color = Cream)
                    ColorPresetRow(
                        presets = FlashcardDisplaySettings.russianColorPresets,
                        selected = draft.russianColorArgb,
                        onSelect = {
                            draft = draft.copy(russianColorArgb = it)
                            onChange(draft)
                        },
                    )
                    Text(stringResource(R.string.word_study_color_bg), color = Cream)
                    ColorPresetRow(
                        presets = FlashcardDisplaySettings.backgroundPresets,
                        selected = draft.backgroundArgb,
                        onSelect = {
                            draft = draft.copy(backgroundArgb = it)
                            onChange(draft)
                        },
                    )
                    TextButton(
                        onClick = {
                            draft = FlashcardDisplaySettings()
                            onChange(draft)
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.word_study_settings_reset), color = HintMuted)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.word_study_settings_done), color = Cream)
                }
            }
        }
    }
}

@Composable
private fun ColorPresetRow(
    presets: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        presets.forEach { argb ->
            val color = Color(argb)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (argb == selected) 3.dp else 1.dp,
                        color = if (argb == selected) Cream else HintMuted,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
            )
        }
    }
}
