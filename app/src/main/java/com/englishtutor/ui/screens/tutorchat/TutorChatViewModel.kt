package com.englishtutor.ui.screens.tutorchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.data.telegram.AetTelegramAuth
import com.englishtutor.data.telegram.AetTelegramUserSession
import com.englishtutor.data.topics.TopicFileDto
import com.englishtutor.data.topics.TopicRepository
import com.englishtutor.data.tutor.EnglishTutorAgentClient
import com.englishtutor.data.tutor.TopicJsonExtractor
import com.englishtutor.data.tutor.TutorAgentPreferences
import com.englishtutor.data.tutor.TutorAgentSettings
import com.englishtutor.data.tutor.TutorChatMessage
import com.englishtutor.data.tutor.TutorChatRole
import com.englishtutor.data.tutor.TutorLinkState
import com.englishtutor.session.WordStudyController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TutorChatUiState(
    val messages: List<TutorChatMessage> = emptyList(),
    val draft: String = "",
    val busy: Boolean = false,
    val status: String = "",
    val lastTopic: TopicFileDto? = null,
    val savedTopicId: String? = null,
    val settings: TutorAgentSettings = TutorAgentSettings(),
    val showSettings: Boolean = false,
    val botUserDraft: String = "",
    val apiIdDraft: String = "",
    val apiHashDraft: String = "",
    val phoneDraft: String = "",
    val telegramAuth: AetTelegramAuth = AetTelegramAuth.Starting,
    val authCodeDraft: String = "",
    val authPasswordDraft: String = "",
    val linkState: TutorLinkState = TutorLinkState.TelegramOffline,
    val lastProbeSnippet: String = "",
)

@HiltViewModel
class TutorChatViewModel @Inject constructor(
    private val agentClient: EnglishTutorAgentClient,
    private val prefs: TutorAgentPreferences,
    private val topicRepository: TopicRepository,
    private val wordStudyController: WordStudyController,
    private val telegram: AetTelegramUserSession,
) : ViewModel() {

    private val _ui = MutableStateFlow(TutorChatUiState())
    val uiState: StateFlow<TutorChatUiState> = _ui.asStateFlow()
    private val autoPhoneSent = AtomicBoolean(false)
    private var profileVerified = false

    init {
        val settings = prefs.load()
        _ui.value = TutorChatUiState(
            settings = settings,
            botUserDraft = settings.telegramBotUsername,
            apiIdDraft = settings.telegramApiId.takeIf { it != 0 }?.toString().orEmpty(),
            apiHashDraft = settings.telegramApiHash,
            phoneDraft = settings.telegramPhone,
            messages = listOf(
                TutorChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = TutorChatRole.System,
                    text = "Миничат → Hermes через Telegram (как AHCC), профиль english_tutor. " +
                        "Inference отключён. Войдите в Telegram, затем «Тест english_tutor».",
                ),
            ),
            linkState = linkFromAuth(telegram.auth.value),
            status = statusLine(telegram.auth.value, linkFromAuth(telegram.auth.value)),
        )
        viewModelScope.launch {
            telegram.auth.collect { auth ->
                if (auth !is AetTelegramAuth.Ready) {
                    profileVerified = false
                }
                val link = when {
                    auth !is AetTelegramAuth.Ready -> TutorLinkState.TelegramOffline
                    profileVerified -> TutorLinkState.EnglishTutorOk
                    _ui.value.linkState == TutorLinkState.Probing -> TutorLinkState.Probing
                    else -> TutorLinkState.TelegramReady
                }
                _ui.update {
                    it.copy(
                        telegramAuth = auth,
                        linkState = link,
                        status = if (it.busy) it.status else statusLine(auth, link),
                    )
                }
            }
        }
        viewModelScope.launch {
            telegram.lastError.collect { err ->
                if (!err.isNullOrBlank() && !_ui.value.busy) {
                    _ui.update { it.copy(status = "Telegram: $err") }
                }
            }
        }
    }

    fun onDraftChange(value: String) = _ui.update { it.copy(draft = value) }

    fun toggleSettings() = _ui.update { it.copy(showSettings = !it.showSettings) }

    fun onBotUserChange(v: String) = _ui.update { it.copy(botUserDraft = v) }
    fun onApiIdChange(v: String) = _ui.update { it.copy(apiIdDraft = v) }
    fun onApiHashChange(v: String) = _ui.update { it.copy(apiHashDraft = v) }
    fun onPhoneChange(v: String) = _ui.update { it.copy(phoneDraft = v) }
    fun onAuthCodeChange(v: String) = _ui.update { it.copy(authCodeDraft = v) }
    fun onAuthPasswordChange(v: String) = _ui.update { it.copy(authPasswordDraft = v) }

    fun saveSettings() {
        val apiId = _ui.value.apiIdDraft.trim().toIntOrNull() ?: 0
        val settings = TutorAgentSettings(
            telegramBotUsername = _ui.value.botUserDraft,
            telegramApiId = apiId,
            telegramApiHash = _ui.value.apiHashDraft,
            telegramPhone = _ui.value.phoneDraft,
            telegramBotToken = _ui.value.settings.telegramBotToken,
            telegramChatId = _ui.value.settings.telegramChatId,
        )
        prefs.save(settings)
        profileVerified = false
        _ui.update {
            it.copy(
                settings = settings,
                showSettings = false,
                linkState = if (telegram.isReady) TutorLinkState.TelegramReady else TutorLinkState.TelegramOffline,
                status = "Настройки сохранены · Telegram → english_tutor",
            )
        }
        if (apiId != 0 && settings.telegramApiHash.isNotBlank()) {
            telegram.begin(apiId, settings.telegramApiHash)
        }
    }

    fun submitTelegramCode() {
        val code = _ui.value.authCodeDraft.trim()
        if (code.isEmpty()) return
        telegram.submitCode(code)
        _ui.update { it.copy(authCodeDraft = "", status = "Код отправлен…") }
    }

    fun submitTelegramPassword() {
        val password = _ui.value.authPasswordDraft
        if (password.isEmpty()) return
        telegram.submitPassword(password)
        _ui.update { it.copy(authPasswordDraft = "", status = "Пароль отправлен…") }
    }

    fun submitTelegramPhone() {
        val phone = _ui.value.phoneDraft.ifBlank { _ui.value.settings.telegramPhone }
        if (phone.isBlank()) {
            _ui.update { it.copy(status = "Укажите телефон Telegram") }
            return
        }
        if (!telegram.isReady && _ui.value.settings.telegramApiId != 0) {
            telegram.begin(_ui.value.settings.telegramApiId, _ui.value.settings.telegramApiHash)
        }
        autoPhoneSent.set(true)
        telegram.submitPhone(phone)
        _ui.update { it.copy(status = "Телефон отправлен… Код — в чате «Telegram», не SMS.") }
    }

    fun resendTelegramCode() {
        telegram.resendCode()
        _ui.update { it.copy(status = "Запрос повторного кода…") }
    }

    fun resetTelegramSession() {
        val s = _ui.value.settings
        if (s.telegramApiId == 0 || s.telegramApiHash.isBlank()) {
            _ui.update { it.copy(status = "Нет api_id / api_hash") }
            return
        }
        autoPhoneSent.set(false)
        profileVerified = false
        telegram.resetSession(s.telegramApiId, s.telegramApiHash)
        _ui.update {
            it.copy(
                linkState = TutorLinkState.TelegramOffline,
                lastProbeSnippet = "",
                status = "Сеанс сброшен",
            )
        }
    }

    fun beginQrTelegramLogin() {
        val s = _ui.value.settings
        if (s.telegramApiId == 0 || s.telegramApiHash.isBlank()) {
            _ui.update { it.copy(status = "Нет api_id / api_hash") }
            return
        }
        autoPhoneSent.set(false)
        profileVerified = false
        telegram.beginQrLogin(s.telegramApiId, s.telegramApiHash)
        _ui.update {
            it.copy(
                linkState = TutorLinkState.TelegramOffline,
                status = "QR: подтвердите вход в открытом Telegram",
            )
        }
    }

    /** Real Hermes ping: TO: english_tutor + verify reply. */
    fun sendTestPing() {
        if (_ui.value.busy) return
        if (!telegram.isReady) {
            _ui.update { it.copy(status = "Сначала войдите в Telegram") }
            return
        }
        val userMsg = TutorChatMessage(
            id = UUID.randomUUID().toString(),
            role = TutorChatRole.User,
            text = "[тест] ${EnglishTutorAgentClient.TEST_PING}",
        )
        _ui.update {
            it.copy(
                messages = it.messages + userMsg,
                busy = true,
                linkState = TutorLinkState.Probing,
                status = "Проверка english_tutor через Telegram…",
                lastProbeSnippet = "",
            )
        }
        viewModelScope.launch {
            runCatching {
                agentClient.complete(_ui.value.settings, EnglishTutorAgentClient.TEST_PING) { partial ->
                    _ui.update { state ->
                        state.copy(status = "Ответ… ${partial.length} симв.")
                    }
                }
            }.onSuccess { reply ->
                val ok = EnglishTutorAgentClient.looksLikeEnglishTutor(reply)
                profileVerified = ok
                val assistant = TutorChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = TutorChatRole.Assistant,
                    text = reply,
                )
                _ui.update {
                    it.copy(
                        messages = it.messages + assistant,
                        busy = false,
                        linkState = if (ok) TutorLinkState.EnglishTutorOk else TutorLinkState.EnglishTutorFailed,
                        lastProbeSnippet = reply.take(160),
                        status = if (ok) {
                            "✓ english_tutor отвечает через Telegram/Hermes"
                        } else {
                            "Telegram OK, но ответ не похож на english_tutor"
                        },
                    )
                }
            }.onFailure { e ->
                profileVerified = false
                _ui.update {
                    it.copy(
                        busy = false,
                        linkState = TutorLinkState.EnglishTutorFailed,
                        status = e.message ?: "Ошибка теста",
                        messages = it.messages + TutorChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = TutorChatRole.System,
                            text = "Тест english_tutor: ${e.message ?: e::class.java.simpleName}",
                        ),
                    )
                }
            }
        }
    }

    fun sendQuick(topicHint: String) {
        val prompt =
            "Сгенерируй offline study topic JSON для AndroidEnglishTutor на тему: $topicHint. " +
                "Только один ```json``` блок по схеме id/titleRu/words/phrases/sentences."
        _ui.update { it.copy(draft = prompt) }
        send()
    }

    fun send() {
        val text = _ui.value.draft.trim()
        if (text.isEmpty() || _ui.value.busy) return
        if (!telegram.isReady) {
            _ui.update { it.copy(status = "Сначала войдите в Telegram") }
            return
        }
        val userMsg = TutorChatMessage(
            id = UUID.randomUUID().toString(),
            role = TutorChatRole.User,
            text = text,
        )
        _ui.update {
            it.copy(
                messages = it.messages + userMsg,
                draft = "",
                busy = true,
                status = "english_tutor через Telegram…",
                lastTopic = null,
                savedTopicId = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                agentClient.complete(_ui.value.settings, text) { partial ->
                    _ui.update { state ->
                        state.copy(status = "Ответ… ${partial.length} симв.")
                    }
                }
            }.onSuccess { reply ->
                if (EnglishTutorAgentClient.looksLikeEnglishTutor(reply)) {
                    profileVerified = true
                }
                val topic = TopicJsonExtractor.extract(reply)
                val assistant = TutorChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = TutorChatRole.Assistant,
                    text = reply,
                    topicDraft = topic,
                )
                _ui.update {
                    it.copy(
                        messages = it.messages + assistant,
                        busy = false,
                        lastTopic = topic,
                        linkState = when {
                            profileVerified -> TutorLinkState.EnglishTutorOk
                            telegram.isReady -> TutorLinkState.TelegramReady
                            else -> TutorLinkState.TelegramOffline
                        },
                        status = if (topic != null) {
                            "Тема «${topic.titleRu}»: ${topic.words.size} слов — можно сохранить offline."
                        } else {
                            "Ответ получен (JSON темы не найден)."
                        },
                    )
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(
                        busy = false,
                        status = e.message ?: "Ошибка запроса",
                        messages = it.messages + TutorChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = TutorChatRole.System,
                            text = "Ошибка: ${e.message ?: e::class.java.simpleName}",
                        ),
                    )
                }
            }
        }
    }

    fun saveLastTopicOffline() {
        val topic = _ui.value.lastTopic ?: return
        viewModelScope.launch {
            runCatching {
                topicRepository.saveGenerated(topic)
                wordStudyController.refreshStudyStats()
            }.onSuccess {
                _ui.update {
                    it.copy(
                        savedTopicId = topic.id,
                        status = "Сохранено offline: ${topic.titleRu} (${topic.id}). Доступно в Word Study.",
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(status = "Не удалось сохранить: ${e.message}") }
            }
        }
    }

    private fun linkFromAuth(auth: AetTelegramAuth): TutorLinkState =
        if (auth is AetTelegramAuth.Ready) {
            if (profileVerified) TutorLinkState.EnglishTutorOk else TutorLinkState.TelegramReady
        } else {
            TutorLinkState.TelegramOffline
        }

    private fun statusLine(auth: AetTelegramAuth, link: TutorLinkState): String {
        val tg = when (auth) {
            is AetTelegramAuth.Ready -> "Telegram Ready"
            is AetTelegramAuth.NeedPhone -> "Telegram · телефон"
            is AetTelegramAuth.NeedCode -> "Telegram · код (${auth.via})"
            is AetTelegramAuth.NeedPassword -> "Telegram · 2FA"
            is AetTelegramAuth.NeedOtherDevice -> "Telegram · подтвердите на устройстве"
            is AetTelegramAuth.Failed -> "Telegram · ${auth.message}"
            is AetTelegramAuth.Starting -> "Telegram · старт…"
        }
        val profile = when (link) {
            TutorLinkState.TelegramOffline -> "english_tutor · нет канала"
            TutorLinkState.TelegramReady -> "english_tutor · не проверен (нажмите Тест)"
            TutorLinkState.Probing -> "english_tutor · проверка…"
            TutorLinkState.EnglishTutorOk -> "english_tutor · подключён"
            TutorLinkState.EnglishTutorFailed -> "english_tutor · нет ответа профиля"
        }
        return "$tg · $profile"
    }

    companion object {
        val QUICK_TOPICS = listOf(
            "аэропорт и путешествия",
            "офис и совещания",
            "здоровье и спорт",
            "покупки в магазине",
            "погода и природа",
        )
    }
}
