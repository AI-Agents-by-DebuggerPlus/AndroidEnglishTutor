package com.englishtutor.data.voice

import android.content.Context
import com.englishtutor.ui.flashcards.FlashcardDisplaySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Persists flashcard display settings for word study (AHCC-style visuals).
 * Legacy en_sp / ru_sp keys are migrated once if present.
 */
@Singleton
class StudyFontPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _display = MutableStateFlow(load())
    val display: StateFlow<FlashcardDisplaySettings> = _display.asStateFlow()

    fun getDisplay(): FlashcardDisplaySettings = _display.value

    fun updateDisplay(settings: FlashcardDisplaySettings) {
        val normalized = settings.copy(
            englishSp = settings.englishSp.coerceIn(18, 72),
            russianSp = settings.russianSp.coerceIn(14, 48),
            blankAfterSeconds = settings.blankAfterSeconds.coerceIn(5, 300),
            englishTopPaddingDp = settings.englishTopPaddingDp.coerceIn(0, 160),
            enRuGapDp = settings.enRuGapDp.coerceIn(0, 120),
            russianSmallerPercent = settings.russianSmallerPercent.coerceIn(10, 55),
        )
        prefs.edit()
            .putInt(KEY_EN_SP_INT, normalized.englishSp)
            .putInt(KEY_RU_SP_INT, normalized.russianSp)
            .putInt(KEY_EN_COLOR, normalized.englishColorArgb)
            .putInt(KEY_RU_COLOR, normalized.russianColorArgb)
            .putInt(KEY_BG, normalized.backgroundArgb)
            .putInt(KEY_BLANK_AFTER, normalized.blankAfterSeconds)
            .putInt(KEY_EN_TOP, normalized.englishTopPaddingDp)
            .putInt(KEY_EN_RU_GAP, normalized.enRuGapDp)
            .putInt(KEY_RU_SMALLER, normalized.russianSmallerPercent)
            .remove(KEY_EN_SP)
            .remove(KEY_RU_SP)
            .apply()
        _display.update { normalized }
    }

    private fun load(): FlashcardDisplaySettings {
        val defaults = FlashcardDisplaySettings()
        val hasNew = prefs.contains(KEY_EN_SP_INT)
        val enSp = when {
            hasNew -> prefs.getInt(KEY_EN_SP_INT, defaults.englishSp)
            prefs.contains(KEY_EN_SP) -> prefs.getFloat(KEY_EN_SP, DEFAULT_EN_SP).toInt()
            else -> defaults.englishSp
        }.coerceIn(18, 72)
        val ruSp = when {
            hasNew -> prefs.getInt(KEY_RU_SP_INT, defaults.russianSp)
            prefs.contains(KEY_RU_SP) -> prefs.getFloat(KEY_RU_SP, DEFAULT_RU_SP).toInt()
            else -> defaults.russianSp
        }.coerceIn(14, 48)
        return FlashcardDisplaySettings(
            englishSp = enSp,
            russianSp = ruSp,
            englishColorArgb = prefs.getInt(KEY_EN_COLOR, defaults.englishColorArgb),
            russianColorArgb = prefs.getInt(KEY_RU_COLOR, defaults.russianColorArgb),
            backgroundArgb = prefs.getInt(KEY_BG, defaults.backgroundArgb),
            blankAfterSeconds = prefs.getInt(KEY_BLANK_AFTER, defaults.blankAfterSeconds),
            englishTopPaddingDp = prefs.getInt(KEY_EN_TOP, defaults.englishTopPaddingDp),
            enRuGapDp = prefs.getInt(KEY_EN_RU_GAP, defaults.enRuGapDp),
            russianSmallerPercent = prefs.getInt(KEY_RU_SMALLER, defaults.russianSmallerPercent),
        )
    }

    companion object {
        private const val PREFS = "study_font_prefs"
        private const val KEY_EN_SP = "en_sp"
        private const val KEY_RU_SP = "ru_sp"
        private const val KEY_EN_SP_INT = "flashcard_en_sp"
        private const val KEY_RU_SP_INT = "flashcard_ru_sp"
        private const val KEY_EN_COLOR = "flashcard_en_color"
        private const val KEY_RU_COLOR = "flashcard_ru_color"
        private const val KEY_BG = "flashcard_bg_color"
        private const val KEY_BLANK_AFTER = "flashcard_blank_after_sec"
        private const val KEY_EN_TOP = "flashcard_en_top_pad"
        private const val KEY_EN_RU_GAP = "flashcard_en_ru_gap"
        private const val KEY_RU_SMALLER = "flashcard_ru_smaller_pct"
        const val DEFAULT_EN_SP = 40f
        const val DEFAULT_RU_SP = 28f
    }
}
