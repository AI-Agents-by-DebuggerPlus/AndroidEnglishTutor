package com.englishtutor.data.supabase

import com.englishtutor.util.AppLogger
import com.englishtutor.util.LogEntry
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Sends local log buffer to Supabase `public.messages` as `[LOG:…]`
 * per AndroidEnglishTutor-Supabase-Logs-Protocol.
 */
@Singleton
class SupabaseLogRepository @Inject constructor(
    private val settingsRepository: SupabaseSettingsRepository,
    private val logger: AppLogger,
) {
    private val mutex = Mutex()
    private var client: SupabaseClient? = null
    private var cachedUserId: String? = null
    private val sentIds = mutableSetOf<Long>()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun sendBuffer(entries: List<LogEntry>): Result<LogUploadResult> = mutex.withLock {
        runCatching {
            val settings = settingsRepository.getSettings()
            if (settings.supabaseUrl.isBlank() || settings.supabaseAnonKey.isBlank()) {
                error("Заполните supabaseUrl и supabaseAnonKey в assets/default_settings.json")
            }

            ensureClient(settings)
            val userId = ensureSessionLocked(settings)
            val active = client ?: error("Supabase не подключён")

            var sent = 0
            var failed = 0
            var skipped = 0

            for (entry in entries) {
                if (settings.skipDuplicateLogsToSupabase && entry.id in sentIds) {
                    skipped++
                    continue
                }
                val content = LogMessageFormat.buildContent(entry.tag, formatMessage(entry))
                val row = MessageInsert(
                    senderId = userId,
                    senderName = settings.senderName.trim().ifEmpty { "AndroidEnglishTutor" },
                    recipientName = settings.logRecipientName.trim().ifEmpty { "WpfChat" },
                    content = content,
                    createdAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()),
                )
                val insertResult = runCatching {
                    active.postgrest.from("messages").insert(row)
                }
                if (insertResult.isSuccess) {
                    sentIds += entry.id
                    sent++
                } else {
                    failed++
                    val reason = insertResult.exceptionOrNull()?.message ?: "unknown"
                    logger.e(TAG, "INSERT failed for id=${entry.id}: $reason")
                }
            }

            val result = LogUploadResult(sent = sent, failed = failed, skipped = skipped)
            logger.i(TAG, "Log batch sent: ${result.summary}")
            // Also push a meta log line for the receiver
            if (sent > 0 || failed > 0) {
                runCatching {
                    val meta = MessageInsert(
                        senderId = userId,
                        senderName = settings.senderName.trim().ifEmpty { "AndroidEnglishTutor" },
                        recipientName = settings.logRecipientName.trim().ifEmpty { "WpfChat" },
                        content = LogMessageFormat.buildContent(
                            "Supabase",
                            "Log batch sent: $sent ok, $failed failed, $skipped skipped",
                        ),
                        createdAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()),
                    )
                    active.postgrest.from("messages").insert(meta)
                }
            }
            result
        }
    }

    fun clearSentMarks() {
        sentIds.clear()
    }

    private fun formatMessage(entry: LogEntry): String {
        return "${entry.level.name} ${entry.timeLabel} ${entry.message}"
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
        cachedUserId = null
    }

    private suspend fun ensureSessionLocked(settings: SupabaseLogSettings): String {
        val active = client ?: error("Supabase не подключён")
        val existing = active.auth.currentUserOrNull()?.id
        if (!existing.isNullOrBlank()) {
            cachedUserId = existing
            return existing
        }
        if (!settings.useAnonymousAuth) {
            error("Нет сессии. Включите useAnonymousAuth")
        }
        active.auth.signInAnonymously()
        val userId = active.auth.currentUserOrNull()?.id
            ?: error("Анонимная сессия не создана")
        cachedUserId = userId
        logger.i(TAG, "Anonymous session ok uid=$userId")
        return userId
    }

    companion object {
        private const val TAG = "Supabase"
    }
}
