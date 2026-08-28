package com.englishtutor.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class LogEntry(
    val id: Long,
    val timestampMs: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMs))
}

@Singleton
class AppLogger @Inject constructor() {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()
    private var nextId = 0L

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)

    fun e(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun log(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = ++nextId,
            timestampMs = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
        )
        _entries.update { current ->
            (current + entry).takeLast(MAX_ENTRIES)
        }
        android.util.Log.println(
            when (level) {
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARN -> android.util.Log.WARN
                LogLevel.ERROR -> android.util.Log.ERROR
            },
            "EnglishTutor/$tag",
            message,
        )
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}
