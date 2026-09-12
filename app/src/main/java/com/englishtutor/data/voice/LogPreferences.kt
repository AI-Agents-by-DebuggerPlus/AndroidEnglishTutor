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
class LogPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _clearOnRestart = MutableStateFlow(prefs.getBoolean(KEY_CLEAR_ON_RESTART, false))
    val clearOnRestart: StateFlow<Boolean> = _clearOnRestart.asStateFlow()

    fun getClearOnRestart(): Boolean = _clearOnRestart.value

    fun setClearOnRestart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAR_ON_RESTART, enabled).apply()
        _clearOnRestart.update { enabled }
    }

    companion object {
        private const val PREFS = "log_prefs"
        private const val KEY_CLEAR_ON_RESTART = "clear_on_restart"
    }
}
