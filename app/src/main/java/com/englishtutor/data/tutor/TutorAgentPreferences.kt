package com.englishtutor.data.tutor

import android.content.Context
import androidx.core.content.edit
import com.englishtutor.data.supabase.SupabaseSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorAgentPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabaseSettingsRepository: SupabaseSettingsRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): TutorAgentSettings {
        val asset = supabaseSettingsRepository.getSettings()
        // Drop any leftover inference transport preference.
        if (prefs.contains(KEY_TRANSPORT)) {
            prefs.edit { remove(KEY_TRANSPORT) }
        }
        return TutorAgentSettings(
            telegramBotUsername = prefs.getString(KEY_BOT_USER, null)?.takeIf { it.isNotBlank() }
                ?: asset.telegramBotUsername.ifBlank { "GoraAcademyFirstBot" },
            telegramApiId = prefs.getInt(KEY_API_ID, 0).takeIf { it != 0 }
                ?: asset.telegramApiId,
            telegramApiHash = prefs.getString(KEY_API_HASH, null)?.takeIf { it.isNotBlank() }
                ?: asset.telegramApiHash,
            telegramPhone = prefs.getString(KEY_PHONE, null)?.takeIf { it.isNotBlank() }
                ?: asset.telegramPhone,
            telegramBotToken = prefs.getString(KEY_BOT_TOKEN, null)?.takeIf { it.isNotBlank() }
                ?: asset.telegramBotToken,
            telegramChatId = prefs.getString(KEY_CHAT_ID, null)?.takeIf { it.isNotBlank() }
                ?: asset.telegramChatId,
        )
    }

    fun save(settings: TutorAgentSettings) {
        prefs.edit {
            remove(KEY_TRANSPORT)
            remove(KEY_API)
            remove(KEY_BASE)
            remove(KEY_MODEL)
            putString(KEY_BOT_USER, settings.telegramBotUsername.trim().removePrefix("@"))
            putInt(KEY_API_ID, settings.telegramApiId)
            putString(KEY_API_HASH, settings.telegramApiHash.trim())
            putString(KEY_PHONE, settings.telegramPhone.trim())
            putString(KEY_BOT_TOKEN, settings.telegramBotToken.trim())
            putString(KEY_CHAT_ID, settings.telegramChatId.trim())
        }
    }

    companion object {
        private const val PREFS = "tutor_agent"
        private const val KEY_API = "nous_api_key"
        private const val KEY_BASE = "inference_base_url"
        private const val KEY_MODEL = "tutor_model"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_BOT_USER = "telegram_bot_username"
        private const val KEY_API_ID = "telegram_api_id"
        private const val KEY_API_HASH = "telegram_api_hash"
        private const val KEY_PHONE = "telegram_phone"
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
        private const val KEY_CHAT_ID = "telegram_chat_id"
    }
}
