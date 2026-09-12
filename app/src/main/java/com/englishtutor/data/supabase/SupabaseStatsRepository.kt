package com.englishtutor.data.supabase

import com.englishtutor.data.stats.VoiceQuizStatsFile
import com.englishtutor.data.stats.VoiceQuizStatsStore
import com.englishtutor.util.AppLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sync voice-quiz stats JSON via Supabase `messages`
 * (`[STATS:VoiceQuiz]` + payload), same transport as logs.
 */
@Singleton
class SupabaseStatsRepository @Inject constructor(
    private val settingsRepository: SupabaseSettingsRepository,
    private val statsStore: VoiceQuizStatsStore,
    private val logger: AppLogger,
) {
    private val mutex = Mutex()
    private var client: SupabaseClient? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun uploadStats(): Result<String> = mutex.withLock {
        runCatching {
            val settings = requireSettings()
            ensureClient(settings)
            val userId = ensureSessionLocked(settings)
            val active = client ?: error("Supabase не подключён")
            val payload = statsStore.exportJson()
            val content = "${VoiceQuizStatsStore.SERVER_PREFIX}\n$payload"
            val row = MessageInsert(
                senderId = userId,
                senderName = settings.senderName.trim().ifEmpty { "AndroidEnglishTutor" },
                recipientName = settings.logRecipientName.trim().ifEmpty { "WpfChat" },
                content = content,
                createdAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()),
            )
            active.postgrest.from("messages").insert(row)
            logger.i(TAG, "Stats uploaded (${payload.length} chars)")
            "Статистика отправлена на сервер"
        }
    }

    suspend fun downloadAndApply(): Result<VoiceQuizStatsFile> = mutex.withLock {
        runCatching {
            val settings = requireSettings()
            ensureClient(settings)
            ensureSessionLocked(settings)
            val active = client ?: error("Supabase не подключён")
            val sender = settings.senderName.trim().ifEmpty { "AndroidEnglishTutor" }
            val rows = active.postgrest.from("messages").select {
                filter {
                    like("content", "${VoiceQuizStatsStore.SERVER_PREFIX}%")
                    eq("sender_name", sender)
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<MessageRow>()
            val row = rows.firstOrNull()
                ?: error("На сервере нет файла статистики для $sender")
            val rawJson = stripPrefix(row.content)
            val parsed = statsStore.decodeJson(rawJson)
            val applied = statsStore.replaceAll(parsed)
            logger.i(TAG, "Stats downloaded and applied · sessions=${applied.sessions.size}")
            applied
        }
    }

    private fun stripPrefix(content: String): String {
        val trimmed = content.trim()
        val prefix = VoiceQuizStatsStore.SERVER_PREFIX
        return when {
            trimmed.startsWith(prefix) -> trimmed.removePrefix(prefix).trimStart('\n', '\r', ' ')
            else -> error("Неверный формат сообщения статистики")
        }
    }

    private fun requireSettings(): SupabaseLogSettings {
        val settings = settingsRepository.getSettings()
        if (settings.supabaseUrl.isBlank() || settings.supabaseAnonKey.isBlank()) {
            error("Заполните supabaseUrl и supabaseAnonKey в assets/default_settings.json")
        }
        return settings
    }

    private fun ensureClient(settings: SupabaseLogSettings) {
        if (client != null) return
        client = createSupabaseClient(
            supabaseUrl = settings.supabaseUrl.trim(),
            supabaseKey = settings.supabaseAnonKey.trim(),
        ) {
            defaultSerializer = KotlinXSerializer(json)
            install(Auth)
            install(Postgrest)
        }
    }

    private suspend fun ensureSessionLocked(settings: SupabaseLogSettings): String {
        val active = client ?: error("Supabase не подключён")
        val existing = active.auth.currentUserOrNull()?.id
        if (!existing.isNullOrBlank()) return existing
        if (!settings.useAnonymousAuth) {
            error("Нет сессии. Включите useAnonymousAuth")
        }
        active.auth.signInAnonymously()
        return active.auth.currentUserOrNull()?.id
            ?: error("Анонимная сессия не создана")
    }

    companion object {
        private const val TAG = "QuizStatsSync"
    }
}

@Serializable
data class MessageRow(
    val content: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("sender_name")
    val senderName: String = "",
)
