package com.englishtutor.data.tutor

import android.util.Log
import com.englishtutor.data.telegram.AetTelegramUserSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EnglishTutor mini-chat via Telegram user→bot (same path as AHCC).
 * Messages are framed as `TO: english_tutor` for Hermes routing.
 */
@Singleton
class EnglishTutorAgentClient @Inject constructor(
    private val telegram: AetTelegramUserSession,
) {
    suspend fun complete(
        settings: TutorAgentSettings,
        userText: String,
        onPartial: (String) -> Unit = {},
    ): String {
        if (!telegram.isReady) {
            error("Telegram не готов. Войдите как пользователь (телефон/код или QR).")
        }
        val bot = settings.telegramBotUsername.trim().removePrefix("@")
            .ifBlank { "GoraAcademyFirstBot" }
        val wireUser = wireText(userText)
        Log.i(TAG, "Hermes send @$bot chars=${wireUser.length}")
        val reply = telegram.sendTextAndWaitReply(wireUser, bot, onPartial)
        Log.i(TAG, "Hermes reply chars=${reply.length}")
        return reply
    }

    companion object {
        private const val TAG = "AET-Hermes"

        const val PROFILE_ID = "english_tutor"

        const val TEST_PING =
            "Представься одним коротким предложением для AndroidEnglishTutor. " +
                "Обязательно начни ответ словами «Я EnglishTutor»."

        fun wireText(userText: String): String {
            val body = userText.trim()
            if (body.isEmpty()) return body
            if (body.startsWith("TO:", ignoreCase = true)) return body
            return "TO: $PROFILE_ID\n$body"
        }

        /** True when the reply looks like it came from the english_tutor profile. */
        fun looksLikeEnglishTutor(reply: String): Boolean {
            val t = reply.trim()
            if (t.isEmpty()) return false
            val lower = t.lowercase()
            if (lower.contains("я englishtutor")) return true
            if (lower.contains("i am englishtutor") || lower.contains("i'm englishtutor")) return true
            if (Regex("""\benglishtutor\b""").containsMatchIn(lower)) return true
            // SOUL / routing stamps sometimes keep the profile id.
            if (lower.contains("english_tutor")) return true
            return false
        }
    }
}
