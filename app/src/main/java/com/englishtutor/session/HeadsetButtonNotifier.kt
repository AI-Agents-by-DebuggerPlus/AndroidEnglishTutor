package com.englishtutor.session

import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Routes headset media buttons — AHCC HeadsetButtonHub burst pattern.
 * HARDWARE vs SIMULATED labels for BT Play test journal.
 * Multi Play gesture (Play↔Pause toggles, echoes filtered):
 *  - 1×Play → Play
 *  - 2×Play → Next
 *  - 3×Play → Stop (BT test) / next topic (word study)
 *  - 4×Play → Quad (BT test only; one continuous burst, same as 3×)
 * Buds often emit double→MEDIA_NEXT, triple→MEDIA_PREVIOUS (mapped to Stop).
 *
 * Counters are mutually exclusive: one settled gesture increments exactly one of
 * Play / Next / Stop / Quad.
 */
@Singleton
class HeadsetButtonNotifier @Inject constructor(
    private val headsetTestController: HeadsetTestController,
    private val englishTutorPlayHandler: EnglishTutorPlayHandler,
    private val wordStudyController: WordStudyController,
    private val buttonPreferences: HeadsetButtonPreferences,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var lastCommittedKey: String? = null
    private var lastCommittedAtMs: Long = 0L
    private var lastGestureAtMs: Long = 0L
    private var suppressPlayUntilMs: Long = 0L
    private var burstCount: Int = 0
    private var burstJob: Job? = null
    private var burstGeneration: Int = 0
    private var pendingPlaySource: String = "hardware"
    private var pendingPlayLabel: String = "MEDIA_PLAY"

    @Volatile
    var btPlayTestIsolation: Boolean = false

    @Volatile
    var isolatedBtPlayHandler: (() -> Unit)? = null

    fun notifyButton(buttonLabel: String, source: String = "native") {
        val label = HeadsetButtonNames.normalize(buttonLabel)
        scope.launch {
            val kind = eventKind(source)
            val now = System.currentTimeMillis()
            val delta = if (lastGestureAtMs == 0L) -1L else now - lastGestureAtMs
            logger.d(TAG, "$kind in: $label via $source · Δ=${if (delta < 0) "—" else "${delta}ms"}")

            if (HeadsetButtonNames.isBtPlayGestureLabel(label)) {
                handlePlayGesture(label, source, now)
                return@launch
            }

            if (label == "MEDIA_NEXT" || label == "NEXT") {
                handleHardwareNext(label, source, now, kind)
                return@launch
            }

            // Buds: single=Play/Pause, double=Next, triple=Previous — map Previous → Stop.
            if (
                label == "MEDIA_PREVIOUS" || label == "PREVIOUS" ||
                label == "MEDIA_STOP" || label == "STOP"
            ) {
                handleHardwareStop(label, source, now, kind)
                return@launch
            }

            mutex.withLock { cancelPendingPlayLocked() }

            if (btPlayTestIsolation) {
                logger.i(TAG, "Non-play button ignored in isolation: $label ($source)")
                return@launch
            }

            englishTutorPlayHandler.onMediaButton(label, source)
        }
    }

    private suspend fun handleHardwareNext(
        label: String,
        source: String,
        now: Long,
        kind: String,
    ) {
        val suppressMs = btSeriesWindowMs()
        mutex.withLock {
            cancelPendingPlayLocked()
            lastGestureAtMs = 0L
            lastCommittedKey = BT_NEXT_KEY
            lastCommittedAtMs = now
            suppressPlayUntilMs = now + suppressMs
        }
        logger.i(TAG, "$kind: $label via $source → Next (suppress Play ${suppressMs}ms)")
        headsetTestController.recordBtNextEvent(
            source = source,
            kind = kind,
            viaDoublePlay = false,
        )
        if (!btPlayTestIsolation) {
            englishTutorPlayHandler.onMediaButton("MEDIA_NEXT", source)
        }
    }

    private suspend fun handleHardwareStop(
        label: String,
        source: String,
        now: Long,
        kind: String,
    ) {
        val suppressMs = btSeriesWindowMs()
        mutex.withLock {
            cancelPendingPlayLocked()
            lastGestureAtMs = 0L
            lastCommittedKey = BT_STOP_KEY
            lastCommittedAtMs = now
            suppressPlayUntilMs = now + suppressMs
        }
        logger.i(TAG, "$kind: $label via $source → Stop (suppress Play ${suppressMs}ms)")
        headsetTestController.recordBtStopEvent(
            source = source,
            kind = kind,
            viaTriplePlay = false,
            hardwareLabel = label,
        )
        if (!btPlayTestIsolation && wordStudyController.isActive) {
            englishTutorPlayHandler.handleBtNextTopic(source)
        }
    }

    private suspend fun handlePlayGesture(label: String, source: String, now: Long) {
        val kind = eventKind(source)
        val maxBurst = if (btPlayTestIsolation) MAX_BURST_BT_TEST else MAX_BURST_NORMAL
        val update = mutex.withLock {
            if (now < suppressPlayUntilMs) {
                BurstUpdate.Suppressed
            } else if (isSamePressEcho(label, source, now)) {
                BurstUpdate.Echo
            } else {
                val windowMs = btSeriesWindowMs()
                val closed = if (burstCount > 0 && now - lastGestureAtMs > windowMs) {
                    finishBurstLocked()
                } else {
                    null
                }
                val startingFresh = closed != null || burstCount == 0
                burstCount = if (startingFresh) 1 else burstCount + 1
                pendingPlaySource = source
                pendingPlayLabel = label
                lastGestureAtMs = now
                val immediate = if (burstCount >= maxBurst) {
                    burstGeneration++
                    burstJob?.cancel()
                    burstJob = null
                    finishBurstLocked()
                } else {
                    armSettleTimerLocked()
                    null
                }
                BurstUpdate.Counted(closed, burstCount, immediate)
            }
        }
        when (update) {
            BurstUpdate.Suppressed ->
                logger.d(TAG, "Suppressed Play: $label ($source)")
            BurstUpdate.Echo ->
                logger.d(TAG, "Companion Play ignored: $label ($source)")
            is BurstUpdate.Counted -> {
                update.closed?.let { (count, commitSource) ->
                    logger.i(TAG, "window closed → ${count}× via $commitSource")
                    commitBurstResult(count, commitSource)
                }
                update.immediate?.let { (count, commitSource) ->
                    logger.i(TAG, "burst complete → ${count}× via $commitSource")
                    commitBurstResult(count, commitSource)
                }
                if (update.immediate == null) {
                    headsetTestController.setPendingBurstCount(update.multiplicity)
                }
                logger.d(TAG, "$kind: multiplicity ${update.multiplicity} via $source")
            }
        }
    }

    private fun btSeriesWindowMs(): Long {
        val base = buttonPreferences.nextDoubleTapMs.coerceAtLeast(MIN_BT_SERIES_MS)
        return if (btPlayTestIsolation) {
            base.coerceAtLeast(MIN_BT_TEST_SERIES_MS)
        } else {
            base
        }
    }

    /** After 3 taps in BT test, wait a bit longer so a 4th can join the same burst. */
    private fun settleWaitMsLocked(): Long {
        val base = btSeriesWindowMs()
        return if (btPlayTestIsolation && burstCount >= 3) {
            base.coerceAtLeast(MIN_BT_TEST_AFTER_TRIPLE_MS)
        } else {
            base
        }
    }

    private fun armSettleTimerLocked() {
        val generation = ++burstGeneration
        val wait = settleWaitMsLocked()
        burstJob?.cancel()
        burstJob = scope.launch {
            delay(wait)
            val commit = mutex.withLock {
                if (generation != burstGeneration || burstCount == 0) {
                    null
                } else {
                    finishBurstLocked()
                }
            }
            if (commit != null) {
                logger.i(TAG, "settle → ${commit.first}× via ${commit.second}")
                commitBurstResult(commit.first, commit.second)
            }
        }
    }

    private fun finishBurstLocked(): Pair<Int, String>? {
        if (burstCount == 0) return null
        val count = burstCount
        val source = pendingPlaySource
        burstCount = 0
        headsetTestController.setPendingBurstCount(0)
        lastCommittedKey = when {
            count >= 4 -> BT_QUAD_KEY
            count >= 3 -> if (wordStudyController.isActive) BT_TOPIC_KEY else BT_STOP_KEY
            count == 2 -> BT_NEXT_KEY
            else -> BT_PLAY_KEY
        }
        lastCommittedAtMs = System.currentTimeMillis()
        return count to source
    }

    /** UI helper: emit [taps] Play events in one burst (gap between presses only). */
    fun simulatePlayBurst(taps: Int) {
        val count = taps.coerceIn(1, 4)
        scope.launch {
            repeat(count) { index ->
                notifyButton("MEDIA_PLAY", source = "ui-simulate")
                if (index < count - 1) delay(SIMULATE_BURST_GAP_MS)
            }
        }
    }

    private suspend fun commitBurstResult(tapCount: Int, source: String) {
        val kind = eventKind(source)
        when {
            tapCount >= 4 -> {
                logger.i(TAG, "$kind: 4×Play via $source → Quad")
                headsetTestController.recordBtQuadEvent(source = source, kind = kind)
                suppressPlayBriefly()
            }
            tapCount >= 3 && wordStudyController.isActive && !btPlayTestIsolation -> {
                logger.i(TAG, "$kind: 3×Play via $source → next topic")
                suppressPlayBriefly()
                englishTutorPlayHandler.handleBtNextTopic(source)
            }
            tapCount >= 3 -> {
                logger.i(TAG, "$kind: 3×Play via $source → Stop")
                headsetTestController.recordBtStopEvent(
                    source = source,
                    kind = kind,
                    viaTriplePlay = true,
                )
                suppressPlayBriefly()
            }
            tapCount == 2 -> {
                logger.i(TAG, "$kind: 2×Play via $source → Next")
                headsetTestController.recordBtNextEvent(
                    source = source,
                    kind = kind,
                    viaDoublePlay = true,
                )
                suppressPlayBriefly()
                if (!btPlayTestIsolation) {
                    englishTutorPlayHandler.onMediaButton("MEDIA_NEXT", source)
                }
            }
            else -> {
                dispatchPlay("MEDIA_PLAY", source)
            }
        }
    }

    private suspend fun suppressPlayBriefly() {
        val ms = btSeriesWindowMs()
        mutex.withLock {
            // Drop echo taps after a committed multi-gesture so they cannot settle as Play.
            cancelBurstOnlyLocked()
            suppressPlayUntilMs = System.currentTimeMillis() + ms
        }
    }

    /**
     * One physical click often arrives twice (PLAY+PAUSE, or mediaButton + onPlay).
     * Headset toggles alternate PLAY / PAUSE per physical press — a PAUSE ~300–600ms after
     * PLAY is a new press (tap 2), not an echo. Only tight PLAY↔PAUSE pairs are echoes.
     */
    private fun isSamePressEcho(label: String, source: String, now: Long): Boolean {
        if (lastGestureAtMs == 0L) return false
        val delta = now - lastGestureAtMs
        val playPausePair = isPauseLabel(label) != isPauseLabel(pendingPlayLabel)
        if (playPausePair) return delta < TOGGLE_ECHO_MS
        if (delta >= ECHO_MS) return false
        if (source != pendingPlaySource) return true
        if (!isPauseLabel(label) && !isPauseLabel(pendingPlayLabel)) return false
        if (isPauseLabel(label) && isPauseLabel(pendingPlayLabel)) return delta < DUPLICATE_MS
        return false
    }

    private fun isPauseLabel(label: String): Boolean {
        val n = HeadsetButtonNames.normalize(label)
        return n == "MEDIA_PAUSE" || n == "PAUSE"
    }

    private suspend fun dispatchPlay(label: String, source: String) {
        val kind = eventKind(source)
        headsetTestController.recordBtPlayEvent(
            label = if (HeadsetButtonNames.isBtPlayLabel(label)) label else "MEDIA_PLAY",
            source = source,
            kind = kind,
        )
        if (btPlayTestIsolation) {
            val handler = isolatedBtPlayHandler
            if (handler != null) {
                logger.i(TAG, "$kind: $label via $source → isolated handler")
                withContext(Dispatchers.Main) { handler() }
            } else {
                logger.i(TAG, "$kind: $label via $source → isolation (counter only)")
            }
            return
        }
        logger.d(TAG, "$kind: $label via $source → lesson handler")
        englishTutorPlayHandler.handleBtPlay(source)
    }

    private fun cancelBurstOnlyLocked() {
        burstGeneration++
        burstJob?.cancel()
        burstJob = null
        burstCount = 0
        lastGestureAtMs = 0L
        pendingPlaySource = "hardware"
        pendingPlayLabel = "MEDIA_PLAY"
        headsetTestController.setPendingBurstCount(0)
    }

    private fun cancelPendingPlayLocked() {
        cancelBurstOnlyLocked()
        headsetTestController.setAwaitingSecondDouble(false)
    }

    /** Clears in-flight burst when the user resets BT test counters. */
    fun resetTestGestures() {
        scope.launch {
            mutex.withLock {
                cancelPendingPlayLocked()
                suppressPlayUntilMs = 0L
                lastCommittedKey = null
                lastCommittedAtMs = 0L
            }
            logger.i(TAG, "BT test gesture state reset")
        }
    }

    private sealed class BurstUpdate {
        data object Suppressed : BurstUpdate()
        data object Echo : BurstUpdate()
        data class Counted(
            val closed: Pair<Int, String>?,
            val multiplicity: Int,
            val immediate: Pair<Int, String>? = null,
        ) : BurstUpdate()
    }

    companion object {
        private const val TAG = "Headset"
        private const val BT_PLAY_KEY = "BT_PLAY"
        private const val BT_NEXT_KEY = "BT_NEXT"
        private const val BT_STOP_KEY = "BT_STOP"
        private const val BT_TOPIC_KEY = "BT_TOPIC"
        private const val BT_QUAD_KEY = "BT_QUAD"
        private const val DUPLICATE_MS = 45L
        private const val ECHO_MS = 220L
        private const val TOGGLE_ECHO_MS = 180L
        private const val MIN_BT_SERIES_MS = 900L
        private const val MIN_BT_TEST_SERIES_MS = 1_200L
        /** Extra settle time after the 3rd tap so a 4th can still join. */
        private const val MIN_BT_TEST_AFTER_TRIPLE_MS = 1_800L
        private const val MAX_BURST_BT_TEST = 4
        private const val MAX_BURST_NORMAL = 3
        private const val SIMULATE_BURST_GAP_MS = 280L

        fun eventKind(source: String): String {
            val s = source.lowercase()
            return if (
                s.contains("hardware") ||
                s.contains("mediabuttonevent") ||
                s.contains("callback-on")
            ) {
                "HARDWARE"
            } else {
                "SIMULATED"
            }
        }
    }
}
