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
    val nextCount: Int = 0,
    val stopCount: Int = 0,
    val quadCount: Int = 0,
    /** Current unfinished Play burst (1..3) while waiting for settle. */
    val pendingBurstCount: Int = 0,
    /** BT test: first 2× settled; waiting for another 2× → Quad. */
    val awaitingSecondDouble: Boolean = false,
    val lastEventLabel: String = "",
    val lastEventAt: String = "",
    val lastEventKind: String = "",
    val eventLog: List<String> = emptyList(),
    val statusMessage: String? = null,
)

/**
 * UI state for BT Play test tab — Play/Next/Stop/4× counters and HARDWARE/SIMULATED event log.
 */
@Singleton
class HeadsetTestController @Inject constructor(
    private val logger: AppLogger,
) {
    private val _state = MutableStateFlow(HeadsetTestState())
    val state: StateFlow<HeadsetTestState> = _state.asStateFlow()
    private var lastLoggedCaptureOn: Boolean? = null
    /** Wall-clock of last Play counter increment (for retracting echo Play after Quad). */
    private var lastPlayAtMs: Long = 0L

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

        lastPlayAtMs = now
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

    /**
     * If a lone Play was counted in [sinceMs]..now (echo after 4×), undo that one increment.
     */
    fun retractPlayCountedSince(sinceMs: Long) {
        if (lastPlayAtMs < sinceMs || lastPlayAtMs == 0L) return
        _state.update { current ->
            if (current.pressCount <= 0) return@update current
            val playCount = current.pressCount - 1
            logger.i(TAG, "Retract Play after Quad → pressCount=$playCount")
            lastPlayAtMs = 0L
            current.copy(
                pressCount = playCount,
                statusMessage = "Play отменён (сработал 4×)",
            )
        }
    }

    fun recordBtNextEvent(
        source: String = "native",
        kind: String = HeadsetButtonNotifier.eventKind(source),
        viaDoublePlay: Boolean = true,
    ) {
        val display = if (viaDoublePlay) "Next (2×Play)" else "Next"
        val now = System.currentTimeMillis()
        val at = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
        logger.i(TAG, "BT button $display via $source ($kind)")

        _state.update { current ->
            val nextCount = current.nextCount + 1
            val line = "$at  [$kind]  $display  (#$nextCount)"
            current.copy(
                nextCount = nextCount,
                lastEventLabel = "$display · $kind",
                lastEventAt = at,
                lastEventKind = kind,
                eventLog = (listOf(line) + current.eventLog).take(MAX_EVENTS),
                statusMessage = "Получено: $display ($kind) · $at",
            )
        }
    }

    fun recordBtStopEvent(
        source: String = "native",
        kind: String = HeadsetButtonNotifier.eventKind(source),
        viaTriplePlay: Boolean = true,
        hardwareLabel: String? = null,
    ) {
        val display = when {
            viaTriplePlay -> "Stop (3×Play)"
            hardwareLabel?.contains("PREV") == true -> "Stop (Prev)"
            else -> "Stop"
        }
        val now = System.currentTimeMillis()
        val at = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
        logger.i(TAG, "BT button $display via $source ($kind)")

        _state.update { current ->
            val stopCount = current.stopCount + 1
            val line = "$at  [$kind]  $display  (#$stopCount)"
            current.copy(
                stopCount = stopCount,
                lastEventLabel = "$display · $kind",
                lastEventAt = at,
                lastEventKind = kind,
                eventLog = (listOf(line) + current.eventLog).take(MAX_EVENTS),
                statusMessage = "Получено: $display ($kind) · $at",
            )
        }
    }

    fun recordBtQuadEvent(
        source: String = "native",
        kind: String = HeadsetButtonNotifier.eventKind(source),
    ) {
        val display = "Quad (4×Play)"
        val now = System.currentTimeMillis()
        val at = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(now))
        logger.i(TAG, "BT button $display via $source ($kind)")

        _state.update { current ->
            val quadCount = current.quadCount + 1
            val line = "$at  [$kind]  $display  (#$quadCount)"
            current.copy(
                quadCount = quadCount,
                lastEventLabel = "$display · $kind",
                lastEventAt = at,
                lastEventKind = kind,
                eventLog = (listOf(line) + current.eventLog).take(MAX_EVENTS),
                statusMessage = "Получено: $display ($kind) · $at",
            )
        }
    }

    fun setPendingBurstCount(count: Int) {
        _state.update { it.copy(pendingBurstCount = count.coerceAtLeast(0)) }
    }

    fun setAwaitingSecondDouble(awaiting: Boolean) {
        _state.update { it.copy(awaitingSecondDouble = awaiting) }
    }

    fun resetCounter() {
        lastPlayAtMs = 0L
        _state.update {
            it.copy(
                pressCount = 0,
                nextCount = 0,
                stopCount = 0,
                quadCount = 0,
                pendingBurstCount = 0,
                awaitingSecondDouble = false,
                lastEventLabel = "",
                lastEventAt = "",
                lastEventKind = "",
                eventLog = emptyList(),
            )
        }
        logger.i(TAG, "BT Play/Next/Stop/4× counters reset")
    }

    companion object {
        private const val TAG = "Headset"
        private const val MAX_EVENTS = 40
    }
}
