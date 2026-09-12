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
class StudyFontPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enSp = MutableStateFlow(prefs.getFloat(KEY_EN_SP, DEFAULT_EN_SP))
    private val _ruSp = MutableStateFlow(prefs.getFloat(KEY_RU_SP, DEFAULT_RU_SP))

    val enSp: StateFlow<Float> = _enSp.asStateFlow()
    val ruSp: StateFlow<Float> = _ruSp.asStateFlow()

    fun getEnSp(): Float = _enSp.value
    fun getRuSp(): Float = _ruSp.value

    fun setEnSp(value: Float) {
        val clamped = value.coerceIn(MIN_SP, MAX_SP)
        prefs.edit().putFloat(KEY_EN_SP, clamped).apply()
        _enSp.update { clamped }
    }

    fun setRuSp(value: Float) {
        val clamped = value.coerceIn(MIN_SP, MAX_SP)
        prefs.edit().putFloat(KEY_RU_SP, clamped).apply()
        _ruSp.update { clamped }
    }

    companion object {
        private const val PREFS = "study_font_prefs"
        private const val KEY_EN_SP = "en_sp"
        private const val KEY_RU_SP = "ru_sp"
        const val DEFAULT_EN_SP = 40f
        const val DEFAULT_RU_SP = 28f
        const val MIN_SP = 16f
        const val MAX_SP = 72f
    }
}
