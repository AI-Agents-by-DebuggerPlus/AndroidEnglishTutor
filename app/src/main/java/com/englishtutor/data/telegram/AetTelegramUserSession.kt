package com.englishtutor.data.telegram

import android.content.Context
import android.os.Build
import android.util.Log
import com.englishtutor.BuildConfig
import com.englishtutor.data.tutor.AgentReplyParts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

sealed class AetTelegramAuth {
    data object Starting : AetTelegramAuth()
    data object NeedPhone : AetTelegramAuth()
    data class NeedCode(val via: String) : AetTelegramAuth()
    data class NeedPassword(val hint: String) : AetTelegramAuth()
    data class NeedOtherDevice(val link: String) : AetTelegramAuth()
    data object Ready : AetTelegramAuth()
    data class Failed(val message: String) : AetTelegramAuth()
}

/**
 * Telegram user session (MTProto via TDLib) — same path as AHCC.
 * Sends as the user to the Hermes bot; Hermes owns getUpdates.
 */
@Singleton
class AetTelegramUserSession @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val filesDir = context.filesDir
    private val databaseDirFile = File(filesDir, "tdlib-aet").apply { mkdirs() }
    private val databaseDir = databaseDirFile.absolutePath
    private var client: Client? = null

    private val _auth = MutableStateFlow<AetTelegramAuth>(AetTelegramAuth.Starting)
    val auth: StateFlow<AetTelegramAuth> = _auth.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var parametersSent = false
    @Volatile private var qrOnPhoneWait = false
    private var botChatId: Long = 0L
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val replyLock = Any()
    private val replyParts = linkedMapOf<Long, String>()
    private val outgoingIds = mutableSetOf<Long>()
    @Volatile private var replyWaiter: CompletableDeferred<String>? = null
    @Volatile private var onReply: ((String) -> Unit)? = null
    private var quietJob: Job? = null

    var apiId: Int = 0
    var apiHash: String = ""

    fun restoreSavedSession(apiId: Int, apiHash: String) {
        if (apiId == 0 || apiHash.isBlank()) return
        val saved = databaseDirFile.walkTopDown().any { it.isFile && it.length() > 0L }
        Log.i(TAG, if (saved) "restoring Telegram session" else "no saved Telegram session — starting")
        begin(apiId, apiHash)
    }

    fun begin(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash.trim()
        if (client == null) {
            parametersSent = false
            _auth.value = AetTelegramAuth.Starting
            client = Client.create(
                { update -> onUpdate(update) },
                { error -> Log.e(TAG, "update exception", error) },
                { error -> Log.e(TAG, "default exception", error) },
            )
        } else if (
            _auth.value is AetTelegramAuth.Failed ||
            _auth.value is AetTelegramAuth.NeedPhone ||
            _auth.value is AetTelegramAuth.Starting
        ) {
            parametersSent = false
            sendParameters()
        }
    }

    fun submitPhone(phone: String) {
        val normalized = phone.trim()
        Log.i(TAG, "submitPhone len=${normalized.length}")
        _lastError.value = null
        client?.send(TdApi.SetAuthenticationPhoneNumber(normalized, null), ::logAuthResult)
            ?: run {
                _auth.value = AetTelegramAuth.Failed("TDLib ещё не запущен — откройте миничат снова")
            }
    }

    fun submitCode(code: String) {
        Log.i(TAG, "submitCode")
        _lastError.value = null
        client?.send(TdApi.CheckAuthenticationCode(code.trim()), ::logAuthResult)
    }

    fun submitPassword(password: String) {
        Log.i(TAG, "submitPassword")
        _lastError.value = null
        client?.send(TdApi.CheckAuthenticationPassword(password), ::logAuthResult)
    }

    fun resendCode() {
        Log.i(TAG, "resendAuthenticationCode")
        _lastError.value = null
        client?.send(TdApi.ResendAuthenticationCode(), ::logAuthResult)
    }

    /** Login by confirming in already-open Telegram (QR / tg:// link) — no SMS code. */
    fun beginQrLogin(apiId: Int, apiHash: String) {
        Log.i(TAG, "beginQrLogin")
        qrOnPhoneWait = true
        resetSession(apiId, apiHash)
    }

    /** Wipe local TDLib DB and start login again (stuck / flood / wrong state). */
    fun resetSession(apiId: Int, apiHash: String) {
        Log.i(TAG, "resetSession")
        _lastError.value = null
        replyWaiter = null
        onReply = null
        quietJob?.cancel()
        botChatId = 0L
        parametersSent = false
        client?.send(TdApi.Close()) { }
        client = null
        _auth.value = AetTelegramAuth.Starting
        runCatching {
            if (databaseDirFile.exists()) {
                databaseDirFile.deleteRecursively()
            }
            databaseDirFile.mkdirs()
        }.onFailure { Log.e(TAG, "reset delete failed", it) }
        begin(apiId, apiHash)
    }

    val isReady: Boolean get() = _auth.value is AetTelegramAuth.Ready

    suspend fun sendTextAndWaitReply(
        text: String,
        botUsername: String,
        onUpdate: (String) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        if (!isReady) error("Сначала войдите в Telegram как пользователь")
        val chatId = ensureBotChat(botUsername)
        val waiter = CompletableDeferred<String>()
        synchronized(replyLock) {
            replyParts.clear()
            outgoingIds.clear()
        }
        quietJob?.cancel()
        onReply = onUpdate
        replyWaiter = waiter
        try {
            val content = TdApi.InputMessageText(
                TdApi.FormattedText(text, emptyArray()),
                null,
                false,
            )
            val sent = sendExpect<TdApi.Message>(
                TdApi.SendMessage(chatId, null, null, null, null, content),
            )
            synchronized(replyLock) { outgoingIds.add(sent.id) }
            Log.i(TAG, "sent to @$botUsername chat=$chatId")
            try {
                withTimeout(WAIT_MS) { waiter.await() }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                val partial = snapshotForUi()
                if (partial.isBlank()) error("Нет текстового ответа за 3 минуты")
                partial
            }
        } finally {
            replyWaiter = null
            onReply = null
            quietJob?.cancel()
        }
    }

    private suspend fun ensureBotChat(username: String): Long {
        if (botChatId != 0L) return botChatId
        val chat = sendExpect<TdApi.Chat>(TdApi.SearchPublicChat(username.removePrefix("@")))
        botChatId = chat.id
        return botChatId
    }

    private fun onUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuth(update.authorizationState)
            is TdApi.UpdateNewMessage -> onIncoming(update.message)
            is TdApi.UpdateMessageContent ->
                acceptText(update.chatId, update.messageId, textOf(update.newContent))
        }
    }

    private fun onAuth(state: TdApi.AuthorizationState) {
        Log.i(TAG, "auth ${state.javaClass.simpleName}")
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                if (qrOnPhoneWait) {
                    qrOnPhoneWait = false
                    Log.i(TAG, "RequestQrCodeAuthentication")
                    client?.send(TdApi.RequestQrCodeAuthentication(longArrayOf()), ::logAuthResult)
                } else {
                    _auth.value = AetTelegramAuth.NeedPhone
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                val via = describeCodeDelivery(state.codeInfo)
                Log.i(TAG, "WaitCode via=$via timeout=${state.codeInfo?.timeout}")
                _auth.value = AetTelegramAuth.NeedCode(via)
            }
            is TdApi.AuthorizationStateWaitPassword ->
                _auth.value = AetTelegramAuth.NeedPassword(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateReady -> {
                Log.i(TAG, "user session ready")
                _auth.value = AetTelegramAuth.Ready
            }
            is TdApi.AuthorizationStateClosed ->
                _auth.value = AetTelegramAuth.Failed("Сеанс Telegram закрыт")
            is TdApi.AuthorizationStateLoggingOut ->
                _auth.value = AetTelegramAuth.Starting
            else -> {
                val name = state.javaClass.simpleName
                if (name.contains("OtherDevice", ignoreCase = true)) {
                    val link = runCatching {
                        state.javaClass.getField("link").get(state) as? String
                    }.getOrNull().orEmpty()
                    _auth.value = AetTelegramAuth.NeedOtherDevice(link)
                }
            }
        }
    }

    private fun describeCodeDelivery(info: TdApi.AuthenticationCodeInfo?): String {
        return when (val t = info?.type) {
            is TdApi.AuthenticationCodeTypeTelegramMessage ->
                "в уже открытый Telegram (чат «Telegram»), не SMS"
            is TdApi.AuthenticationCodeTypeSms -> "по SMS на ${info.phoneNumber}"
            is TdApi.AuthenticationCodeTypeCall -> "голосовым звонком"
            is TdApi.AuthenticationCodeTypeFlashCall -> "flash-call"
            else -> "в Telegram (часто не SMS) · ${t?.javaClass?.simpleName ?: "?"}"
        }
    }

    private fun sendParameters() {
        if (parametersSent) return
        if (apiId == 0 || apiHash.isBlank()) {
            _auth.value = AetTelegramAuth.Failed("Нужны api_id и api_hash с my.telegram.org/apps")
            return
        }
        parametersSent = true
        val parameters = TdApi.SetTdlibParameters().apply {
            useTestDc = false
            databaseDirectory = databaseDir
            filesDirectory = databaseDir
            databaseEncryptionKey = ByteArray(0)
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = this@AetTelegramUserSession.apiId
            apiHash = this@AetTelegramUserSession.apiHash
            systemLanguageCode = "en"
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = Build.VERSION.RELEASE ?: "14"
            applicationVersion = BuildConfig.VERSION_NAME
        }
        Log.i(TAG, "SetTdlibParameters api_id=$apiId")
        client?.send(parameters, ::logAuthResult)
    }

    private fun onIncoming(message: TdApi.Message) {
        if (message.isOutgoing) {
            synchronized(replyLock) {
                outgoingIds.add(message.id)
                replyParts.remove(message.id)
            }
            return
        }
        acceptText(message.chatId, message.id, textOf(message.content))
    }

    private fun acceptText(chatId: Long, messageId: Long, raw: String?) {
        if (botChatId != 0L && chatId != botChatId) return
        if (synchronized(replyLock) { messageId in outgoingIds }) return
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || AgentReplyParts.isOwnEcho(text)) return
        val waiter = replyWaiter ?: return
        val shown = synchronized(replyLock) {
            replyParts[messageId] = text
            visibleReply()
        }
        Log.i(TAG, "reply update chars=${shown.length}")
        if (shown.isNotEmpty()) onReply?.invoke(shown)
        val hasAnswer = synchronized(replyLock) {
            replyParts.values.any { AgentReplyParts.containsAnswer(it) }
        }
        quietJob?.cancel()
        val quietMs = if (hasAnswer) ANSWER_QUIET_MS else PARTIAL_QUIET_MS
        if (!hasAnswer && shown.isBlank()) return
        quietJob = sessionScope.launch {
            delay(quietMs)
            val latest = snapshotForUi()
            if (replyWaiter !== waiter || waiter.isCompleted) return@launch
            if (latest.isBlank()) return@launch
            waiter.complete(latest)
        }
    }

    private fun snapshotForUi(): String = synchronized(replyLock) { visibleReply() }

    private fun visibleReply(): String =
        replyParts.values
            .map { it.trim() }
            .filter { it.isNotEmpty() && !AgentReplyParts.isOwnEcho(it) }
            .joinToString("\n")

    private fun textOf(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessageText -> content.text.text
        is TdApi.MessageAudio -> content.caption?.text
        is TdApi.MessageDocument -> content.caption?.text
        is TdApi.MessagePhoto -> content.caption?.text
        is TdApi.MessageVideo -> content.caption?.text
        is TdApi.MessageVoiceNote -> content.caption?.text
        else -> null
    }

    private fun logAuthResult(result: TdApi.Object) {
        if (result is TdApi.Error) {
            Log.e(TAG, "td error ${result.code}: ${result.message}")
            _lastError.value = "${result.code}: ${result.message}"
            val authNow = _auth.value
            if (authNow !is AetTelegramAuth.NeedCode &&
                authNow !is AetTelegramAuth.NeedPassword &&
                authNow !is AetTelegramAuth.NeedOtherDevice
            ) {
                _auth.value = AetTelegramAuth.Failed(result.message)
            }
        }
    }

    private suspend fun <T : TdApi.Object> sendExpect(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { cont ->
            val current = client ?: run {
                cont.resumeWithException(IllegalStateException("Telegram session is not started"))
                return@suspendCancellableCoroutine
            }
            current.send(function) { result ->
                if (result is TdApi.Error) {
                    cont.resumeWithException(IllegalStateException(result.message))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result as T)
                }
            }
        }

    companion object {
        private const val TAG = "AET-TG-User"
        private const val ANSWER_QUIET_MS = 15_000L
        private const val PARTIAL_QUIET_MS = 4_000L
        private const val WAIT_MS = 180_000L
    }
}
