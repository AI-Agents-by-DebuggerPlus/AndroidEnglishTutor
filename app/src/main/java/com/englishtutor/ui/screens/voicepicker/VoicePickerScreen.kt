package com.englishtutor.ui.screens.voicepicker

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.ui.components.BuildVersionSubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicePickerScreen(
    onBack: () -> Unit,
    viewModel: VoicePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.voice_picker_title))
                        BuildVersionSubtitle()
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearPreferred) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.voice_picker_use_default),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Single LazyColumn so mouse-wheel / trackpad scroll works over the whole screen.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "selected") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp, top = 4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.voice_picker_selected_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(
                                R.string.voice_picker_selected_ru,
                                state.selectedRuDisplayName
                                    ?: stringResource(R.string.voice_picker_use_default),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = stringResource(
                                R.string.voice_picker_selected_en,
                                state.selectedEnDisplayName
                                    ?: stringResource(R.string.voice_picker_use_default),
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.voice_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item(key = "filters") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    FilterChip(
                        selected = state.languageFilter == "ru",
                        onClick = { viewModel.setLanguageFilter("ru") },
                        label = { Text("RU") },
                    )
                    FilterChip(
                        selected = state.languageFilter == "en",
                        onClick = { viewModel.setLanguageFilter("en") },
                        label = { Text("EN") },
                    )
                    FilterChip(
                        selected = state.languageFilter == null,
                        onClick = { viewModel.setLanguageFilter(null) },
                        label = { Text(stringResource(R.string.voice_picker_all)) },
                    )
                }
            }
            state.statusMessage?.let { message ->
                item(key = "status") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            items(state.voices, key = { it.id }) { voice ->
                val selected = voice.id == state.selectedRuVoiceId ||
                    voice.id == state.selectedEnVoiceId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectVoice(voice.id) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { viewModel.selectVoice(voice.id) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(voice.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(
                                R.string.voice_picker_voice_meta,
                                voice.languageTag,
                                voice.quality,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.preview(voice.id) }) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.voice_picker_preview),
                        )
                    }
                }
            }
            item(key = "default_btn") {
                OutlinedButton(
                    onClick = viewModel::clearPreferred,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.voice_picker_use_default))
                }
            }
        }
    }
}
