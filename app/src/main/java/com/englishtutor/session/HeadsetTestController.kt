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
    val nativeCaptureOn: Boolean = false,
    val pressCount: Int = 0,
    val lastEventLabel: String = "",
    val lastEventAt: String = "",
    val lastEventKind: String = "",
    val eventLog: List<String> = emptyList(),
    val statusMessage: String? = null,
)

/**
 * UI state for BT Play test tab — counter and HARDWARE/SIMULATED event log.
 */
@Singleton
class HeadsetTestController @Inject constructor(
    private val logger: AppLogger,
) {
    private val _state = MutableStateFlow(HeadsetTestState())
    val state: StateFlow<HeadsetTestState> = _state.asStateFlow()
    private var lastLoggedCaptureOn: Boolean? = null

    fun setCaptureStatus(nativeCaptureOn: Boolean) {
        _state.update {
            it.copy(
                nativeCaptureOn = nativeCaptureOn,
                statusMessage = if (nativeCaptureOn) {
                    "Native capture: ON (MediaSession)"
                } else {
                    "Native capture: OFF"
                },
            )
        }
        if (lastLoggedCaptureOn != nativeCaptureOn) {
            lastLoggedCaptureOn = nativeCaptureOn
            logger.i(TAG, if (nativeCaptureOn) "Headset monitor ON" else "Headset monitor OFF")
        }
    }

    fun recordBtPlayEvent(
        label: String,
        source: String = "native",
        kind: String = HeadsetButtonNotifier.eventKind(source),
    ) {
        val display = HeadsetButtonNames.displayLabel(HeadsetButtonNames.normalize(label))
        val now = System.currentTimeMillis()
        val at = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
        logger.i(TAG, "BT button $display via $source ($kind)")

        _state.update { current ->
            val playCount = current.pressCount + 1
            val line = "$at  [$kind]  $display  (#$playCount)"
            current.copy(
                pressCount = playCount,
                lastEventLabel = "$display · $kind",
                lastEventAt = at,
                lastEventKind = kind,
                eventLog = (listOf(line) + current.eventLog).take(MAX_EVENTS),
                statusMessage = "Получено: $display ($kind) · $at",
            )
        }
    }

    fun resetCounter() {
        _state.update {
            it.copy(
                pressCount = 0,
                lastEventLabel = "",
                lastEventAt = "",
                lastEventKind = "",
                eventLog = emptyList(),
            )
        }
        logger.i(TAG, "BT Play counter reset")
    }

    companion object {
        private const val TAG = "Headset"
        private const val MAX_EVENTS = 40
    }
}
