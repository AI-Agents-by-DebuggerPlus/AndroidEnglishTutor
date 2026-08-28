package com.englishtutor.session

import com.englishtutor.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class HeadsetTestState(
    val isActive: Boolean = false,
    val nativeCaptureOn: Boolean = true,
    val pressCount: Int = 0,
    val lastEventLabel: String = "",
    val lastEventAt: String = "",
    val eventLog: List<String> = emptyList(),
    val statusMessage: String? = null,
)

/**
 * Diagnostic headset button capture — mirrors AndroidChat BT Play test tab.
 * No TTS/STT side effects; only counter + event log.
 */
@Singleton
class HeadsetTestController @Inject constructor(
    private val logger: AppLogger,
) {
    private val debounceLock = Any()
    private var lastSentKey: String? = null
    private var lastSentAtMs: Long = 0L

    private val _state = MutableStateFlow(HeadsetTestState())
    val state: StateFlow<HeadsetTestState> = _state.asStateFlow()

    fun setActive(active: Boolean) {
        _state.update {
            it.copy(
                isActive = active,
                statusMessage = if (active) {
                    "Native capture: ON (MediaSession)"
                } else {
                    "Тест гарнитуры остановлен"
                },
            )
        }
        logger.i(TAG, if (active) "Headset test ACTIVE" else "Headset test STOPPED")
    }

    fun notifyButton(buttonLabel: String, source: String = "native") {
        val label = HeadsetButtonNames.normalize(buttonLabel)
        val now = System.currentTimeMillis()
        val debounceKey = if (HeadsetButtonNames.isBtPlayLabel(label)) BT_PLAY_KEY else label

        synchronized(debounceLock) {
            if (debounceKey == lastSentKey && now - lastSentAtMs < DEBOUNCE_MS) {
                logger.d(TAG, "Debounced: $label ($source)")
                return
            }
            lastSentKey = debounceKey
            lastSentAtMs = now
        }

        if (!_state.value.isActive) {
            logger.w(TAG, "Ignored $label ($source) — test not active")
            return
        }

        val display = HeadsetButtonNames.displayLabel(label)
        val at = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
        logger.i(TAG, "BT button $display via $source")

        if (!HeadsetButtonNames.isBtPlayLabel(label)) {
            return
        }

        _state.update { current ->
            val playCount = current.pressCount + 1
            val line = "$at  $display  (#$playCount)"
            current.copy(
                pressCount = playCount,
                lastEventLabel = display,
                lastEventAt = at,
                eventLog = (listOf(line) + current.eventLog).take(MAX_EVENTS),
                statusMessage = "Получено: $display ($at)",
            )
        }
    }

    fun resetCounter() {
        _state.update {
            it.copy(
                pressCount = 0,
                lastEventLabel = "",
                lastEventAt = "",
                eventLog = emptyList(),
            )
        }
        logger.i(TAG, "BT Play counter reset")
    }

    fun simulatePlay() = notifyButton("MEDIA_PLAY", source = "ui-simulate")

    companion object {
        private const val TAG = "Headset"
        private const val MAX_EVENTS = 40
        private const val DEBOUNCE_MS = 500L
        private const val BT_PLAY_KEY = "BT_PLAY"
    }
}
