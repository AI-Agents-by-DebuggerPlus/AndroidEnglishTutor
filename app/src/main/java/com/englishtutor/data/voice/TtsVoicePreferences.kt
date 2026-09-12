package com.englishtutor.data.voice

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class TtsVoicePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _preferredRu = MutableStateFlow(prefs.getString(KEY_VOICE_RU, null))
    private val _preferredEn = MutableStateFlow(prefs.getString(KEY_VOICE_EN, null))

    val preferredRuVoiceId: StateFlow<String?> = _preferredRu.asStateFlow()
    val preferredEnVoiceId: StateFlow<String?> = _preferredEn.asStateFlow()

    init {
        migrateLegacyIfNeeded()
    }

    fun get(languageCode: String): String? = when (normalizeLang(languageCode)) {
        "ru" -> _preferredRu.value
        "en" -> _preferredEn.value
        else -> null
    }

    fun set(languageCode: String, voiceId: String?) {
        val lang = normalizeLang(languageCode) ?: return
        val cleaned = voiceId?.takeIf { it.isNotBlank() }
        prefs.edit().apply {
            val key = keyFor(lang)
            if (cleaned == null) remove(key) else putString(key, cleaned)
        }.apply()
        when (lang) {
            "ru" -> _preferredRu.update { cleaned }
            "en" -> _preferredEn.update { cleaned }
        }
    }

    fun clear(languageCode: String?) {
        val lang = languageCode?.let { normalizeLang(it) }
        if (lang == null) {
            set("ru", null)
            set("en", null)
        } else {
            set(lang, null)
        }
    }

    private fun migrateLegacyIfNeeded() {
        val legacy = prefs.getString(KEY_VOICE_ID_LEGACY, null)?.takeIf { it.isNotBlank() } ?: return
        val hasRu = !prefs.getString(KEY_VOICE_RU, null).isNullOrBlank()
        val hasEn = !prefs.getString(KEY_VOICE_EN, null).isNullOrBlank()
        if (!hasRu && !hasEn) {
            // Old single preference: keep as RU default (quiz prompts were Russian).
            prefs.edit()
                .putString(KEY_VOICE_RU, legacy)
                .remove(KEY_VOICE_ID_LEGACY)
                .apply()
            _preferredRu.update { legacy }
        } else {
            prefs.edit().remove(KEY_VOICE_ID_LEGACY).apply()
        }
    }

    companion object {
        private const val PREFS = "tts_voice_prefs"
        private const val KEY_VOICE_ID_LEGACY = "preferred_voice_id"
        private const val KEY_VOICE_RU = "preferred_voice_ru"
        private const val KEY_VOICE_EN = "preferred_voice_en"

        fun normalizeLang(languageCode: String): String? {
            val raw = languageCode.trim().lowercase().replace('_', '-')
            if (raw.isBlank()) return null
            val two = raw.take(2)
            return when (two) {
                "ru" -> "ru"
                "en" -> "en"
                else -> two
            }
        }

        private fun keyFor(lang: String): String = when (lang) {
            "ru" -> KEY_VOICE_RU
            "en" -> KEY_VOICE_EN
            else -> "preferred_voice_$lang"
        }
    }
}
