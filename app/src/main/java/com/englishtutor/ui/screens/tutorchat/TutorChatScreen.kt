package com.englishtutor.ui.screens.tutorchat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishtutor.R
import com.englishtutor.data.telegram.AetTelegramAuth
import com.englishtutor.data.tutor.TutorChatMessage
import com.englishtutor.data.tutor.TutorChatRole
import com.englishtutor.data.tutor.TutorLinkState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorChatScreen(
    onBack: () -> Unit,
    viewModel: TutorChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EnglishTutor")
                        Text(
                            "Telegram → Hermes · english_tutor",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            ConnectionBanner(
                linkState = state.linkState,
                auth = state.telegramAuth,
                bot = state.settings.telegramBotUsername,
                snippet = state.lastProbeSnippet,
            )

            Button(
                onClick = viewModel::sendTestPing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                enabled = !state.busy && state.telegramAuth is AetTelegramAuth.Ready,
            ) {
                Text("Тест english_tutor (Telegram → Hermes)")
            }

            if (state.showSettings) {
                SettingsBlock(
                    botUser = state.botUserDraft,
                    apiId = state.apiIdDraft,
                    apiHash = state.apiHashDraft,
                    phone = state.phoneDraft,
                    onBotUser = viewModel::onBotUserChange,
                    onApiId = viewModel::onApiIdChange,
                    onApiHash = viewModel::onApiHashChange,
                    onPhone = viewModel::onPhoneChange,
                    onSave = viewModel::saveSettings,
                )
            }

            TelegramAuthBlock(
                auth = state.telegramAuth,
                phone = state.phoneDraft,
                code = state.authCodeDraft,
                password = state.authPasswordDraft,
                qrLink = (state.telegramAuth as? AetTelegramAuth.NeedOtherDevice)?.link.orEmpty(),
                onPhone = viewModel::onPhoneChange,
                onCode = viewModel::onAuthCodeChange,
                onPassword = viewModel::onAuthPasswordChange,
                onSubmitPhone = viewModel::submitTelegramPhone,
                onSubmitCode = viewModel::submitTelegramCode,
                onSubmitPassword = viewModel::submitTelegramPassword,
                onResendCode = viewModel::resendTelegramCode,
                onResetSession = viewModel::resetTelegramSession,
                onQrLogin = viewModel::beginQrTelegramLogin,
            )

            Text(
                text = state.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            QuickTopicDropdown(
                enabled = !state.busy && state.telegramAuth is AetTelegramAuth.Ready,
                onSelect = viewModel::sendQuick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatBubble(msg)
                }
            }

            if (state.lastTopic != null) {
                Button(
                    onClick = viewModel::saveLastTopicOffline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    enabled = !state.busy,
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text(
                        "Сохранить «${state.lastTopic!!.titleRu}» offline",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 4.dp),
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy && state.telegramAuth is AetTelegramAuth.Ready,
                    maxLines = 5,
                    placeholder = { Text("Сообщение english_tutor…") },
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = !state.busy &&
                        state.draft.isNotBlank() &&
                        state.telegramAuth is AetTelegramAuth.Ready,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickTopicDropdown(
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    val hint = stringResource(R.string.tutor_chat_quick_topic_hint)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected ?: hint,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.tutor_chat_quick_topic_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            TutorChatViewModel.QUICK_TOPICS.forEach { topic ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = topic,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        selected = topic
                        expanded = false
                        onSelect(topic)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    linkState: TutorLinkState,
    auth: AetTelegramAuth,
    bot: String,
    snippet: String,
) {
    val (dot, title, detail) = when (linkState) {
        TutorLinkState.TelegramOffline -> Triple(
            Color(0xFFE57373),
            "Нет Telegram",
            "Войдите — без этого Hermes недоступен",
        )
        TutorLinkState.TelegramReady -> Triple(
            Color(0xFFFFB74D),
            "Telegram Ready · @$bot",
            "Канал есть — нажмите «Тест english_tutor»",
        )
        TutorLinkState.Probing -> Triple(
            Color(0xFF64B5F6),
            "Проверка english_tutor…",
            "TO: english_tutor → @$bot",
        )
        TutorLinkState.EnglishTutorOk -> Triple(
            Color(0xFF81C784),
            "english_tutor подключён",
            "Реальный ответ через Telegram/Hermes",
        )
        TutorLinkState.EnglishTutorFailed -> Triple(
            Color(0xFFE57373),
            "english_tutor не подтверждён",
            "Telegram есть, профиль не ответил как EnglishTutor",
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dot),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(text = detail, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "auth=${auth.javaClass.simpleName.substringAfterLast('$')}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snippet.isNotBlank()) {
                Text(
                    text = "probe: ${snippet.replace('\n', ' ').take(120)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TelegramAuthBlock(
    auth: AetTelegramAuth,
    phone: String,
    code: String,
    password: String,
    qrLink: String,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmitPhone: () -> Unit,
    onSubmitCode: () -> Unit,
    onSubmitPassword: () -> Unit,
    onResendCode: () -> Unit,
    onResetSession: () -> Unit,
    onQrLogin: () -> Unit,
) {
    val context = LocalContext.current
    fun openOfficialTelegram(link: String? = null) {
        val uri = Uri.parse(link?.takeIf { it.isNotBlank() } ?: "tg://resolve?domain=telegram")
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("org.telegram.messenger")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    when (auth) {
        is AetTelegramAuth.NeedPhone -> {
            AuthCard {
                Text("Вход Telegram (как AHCC)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Код — в чате «Telegram», не SMS. Inference отключён.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhone,
                    label = { Text("Телефон (+…)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(onClick = onSubmitPhone, modifier = Modifier.fillMaxWidth()) {
                    Text("Отправить телефон")
                }
                OutlinedButton(onClick = onQrLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Войти через QR в Telegram")
                }
            }
        }
        is AetTelegramAuth.NeedCode -> {
            AuthCard {
                Text("Код Telegram", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Откройте Telegram → чат «Telegram». Куда: ${auth.via}",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = onCode,
                    label = { Text("Код из чата Telegram") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedButton(onClick = onSubmitCode, modifier = Modifier.fillMaxWidth()) {
                    Text("Подтвердить код")
                }
                Button(onClick = { openOfficialTelegram() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Открыть Telegram")
                }
                OutlinedButton(onClick = onQrLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Сброс → вход по QR")
                }
                OutlinedButton(onClick = onResendCode, modifier = Modifier.fillMaxWidth()) {
                    Text("Выслать код ещё раз")
                }
                OutlinedButton(onClick = onResetSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Сбросить сеанс")
                }
            }
        }
        is AetTelegramAuth.NeedPassword -> {
            AuthCard {
                Text("2FA пароль Telegram", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = onPassword,
                    label = { Text(auth.hint.ifBlank { "Пароль" }) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedButton(onClick = onSubmitPassword, modifier = Modifier.fillMaxWidth()) {
                    Text("Войти")
                }
            }
        }
        is AetTelegramAuth.NeedOtherDevice -> {
            AuthCard {
                Text("Подтвердите в Telegram", style = MaterialTheme.typography.titleSmall)
                if (qrLink.isNotBlank()) {
                    Text(qrLink, style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { openOfficialTelegram(qrLink) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Открыть ссылку в Telegram")
                }
                OutlinedButton(onClick = onResetSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Сбросить сеанс")
                }
            }
        }
        is AetTelegramAuth.Failed -> {
            AuthCard {
                Text("Ошибка Telegram: ${auth.message}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onQrLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Войти через QR")
                }
                OutlinedButton(onClick = onResetSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Сбросить сеанс")
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun AuthCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsBlock(
    botUser: String,
    apiId: String,
    apiHash: String,
    phone: String,
    onBotUser: (String) -> Unit,
    onApiId: (String) -> Unit,
    onApiHash: (String) -> Unit,
    onPhone: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Telegram → Hermes (без Inference)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = botUser,
                onValueChange = onBotUser,
                label = { Text("Bot username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiId,
                onValueChange = onApiId,
                label = { Text("api_id") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = apiHash,
                onValueChange = onApiHash,
                label = { Text("api_hash") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = phone,
                onValueChange = onPhone,
                label = { Text("phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: TutorChatMessage) {
    val isUser = message.role == TutorChatRole.User
    val bg = when (message.role) {
        TutorChatRole.User -> MaterialTheme.colorScheme.primaryContainer
        TutorChatRole.Assistant -> MaterialTheme.colorScheme.secondaryContainer
        TutorChatRole.System -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .padding(12.dp),
        ) {
            Text(
                text = when (message.role) {
                    TutorChatRole.User -> "Вы"
                    TutorChatRole.Assistant -> "english_tutor"
                    TutorChatRole.System -> "Система"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .heightIn(max = 360.dp),
            )
            message.topicDraft?.let { topic ->
                Text(
                    text = "Тема: ${topic.titleRu} · ${topic.words.size}w / " +
                        "${topic.phrases.size}p / ${topic.sentences.size}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
