package com.englishtutor.data.supabase

import com.englishtutor.session.WordStudyState
import com.englishtutor.ui.flashcards.FlashcardDisplaySettings
import com.englishtutor.util.AppLogger
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mirror of the Word Study card for AET Desktop.
 * Phone keeps TTS and Bluetooth; this only publishes what is on screen.
 * Transport: existing `messages` row, content prefix [STUDY:Display].
 */
@Singleton
class StudyDisplayPublisher @Inject constructor(
    private val settingsRepository: SupabaseSettingsRepository,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updates = Channel<StudyDisplaySnapshot>(Channel.CONFLATED)
    private val json = Json { encodeDefaults = true }
    private var client: SupabaseClient? = null
    private var lastBody: String? = null

    init {
        scope.launch {
            for (snapshot in updates) {
                publishLocked(snapshot)
            }
        }
    }

    fun publish(snapshot: StudyDisplaySnapshot) {
        updates.trySend(snapshot)
    }

    private suspend fun publishLocked(snapshot: StudyDisplaySnapshot) {
        val settings = settingsRepository.getSettings()
        if (settings.supabaseUrl.isBlank() || settings.supabaseAnonKey.isBlank()) {
            return
        }
        val body = "$PREFIX\n${json.encodeToString(snapshot)}"
        if (body == lastBody) return
        runCatching {
            ensureClient(settings)
            val userId = ensureSession(settings)
            val active = client ?: error("Supabase не подключён")
            active.postgrest.from("messages").insert(
                MessageInsert(
                    senderId = userId,
                    senderName = settings.senderName.trim().ifEmpty { "AndroidEnglishTutor" },
                    recipientName = RECIPIENT,
                    content = body,
                    createdAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()),
                ),
            )
            lastBody = body
            logger.i(
                TAG,
                "Study mirror · blank=${snapshot.blanked} active=${snapshot.active} " +
                    "«${snapshot.topicTitle}» ${snapshot.english.take(40)}",
            )
        }.onFailure {
            logger.e(TAG, "Study mirror failed: ${it.message}")
        }
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

    private suspend fun ensureSession(settings: SupabaseLogSettings): String {
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
        private const val TAG = "StudyMirror"
        const val PREFIX = "[STUDY:Display]"
        const val RECIPIENT = "AETDesktop"
    }
}

@Serializable
data class StudyDisplaySnapshot(
    val active: Boolean = false,
    val blanked: Boolean = false,
    val topicTitle: String = "",
    val stageLabel: String = "",
    val cardIndex: Int = 0,
    val cardCount: Int = 0,
    val english: String = "",
    val russian: String = "",
    val statusMessage: String = "",
    val englishColor: String = "#FFE8F4F3",
    val russianColor: String = "#FF8AA8A6",
    val backgroundColor: String = "#FF000000",
    val epoch: Long = 0L,
    /** Wall-clock end of the blank countdown. Desktop draws the bar from this. */
    val countdownEndsAtEpochMs: Long = 0L,
    val countdownTotalMs: Long = 0L,
    /** Phone screen is black on purpose; Desktop keeps the card and reveals it on Space. */
    val hidden: Boolean = false,
) {
    companion object {
        fun from(
            study: WordStudyState,
            display: FlashcardDisplaySettings,
            blanked: Boolean,
            countdownEndsAtEpochMs: Long = 0L,
            countdownTotalMs: Long = 0L,
            hidden: Boolean = false,
        ): StudyDisplaySnapshot {
            val hasCard = study.english.isNotBlank()
            val counting = hasCard && !blanked && !hidden && countdownEndsAtEpochMs > 0L && countdownTotalMs > 0L
            return StudyDisplaySnapshot(
                active = study.isActive,
                blanked = blanked && study.isActive,
                topicTitle = study.topicTitle,
                stageLabel = study.stage.labelRu,
                cardIndex = if (hasCard) study.wordIndex + 1 else 0,
                cardCount = study.wordCount,
                english = study.english,
                russian = study.russian,
                statusMessage = study.statusMessage,
                englishColor = display.englishColorArgb.toHexColor(),
                russianColor = display.russianColorArgb.toHexColor(),
                backgroundColor = if (blanked) "#FF000000" else display.backgroundArgb.toHexColor(),
                epoch = study.displayEpoch,
                countdownEndsAtEpochMs = if (counting) countdownEndsAtEpochMs else 0L,
                countdownTotalMs = if (counting) countdownTotalMs else 0L,
                hidden = hidden && study.isActive,
            )
        }

        private fun Int.toHexColor(): String = String.format("#%08X", this)
    }
}
