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
 * Routes headset media buttons — AndroidChatBtTest95 HeadsetButtonNotifier pattern.
 * HARDWARE vs SIMULATED labels for BT Play test journal.
 * Optional debounce + double Play/Pause gesture → Next.
 *
 * Pixel Buds double-tap often emits MEDIA_NEXT and MEDIA_PLAY together; after Next,
 * companion Play is suppressed for the Next window.
 */
@Singleton
class HeadsetButtonNotifier @Inject constructor(
    private val headsetTestController: HeadsetTestController,
    private val englishTutorPlayHandler: EnglishTutorPlayHandler,
    private val buttonPreferences: HeadsetButtonPreferences,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var lastCommittedKey: String? = null
    private var lastCommittedAtMs: Long = 0L
    private var lastGestureAtMs: Long = 0L
    private var suppressPlayUntilMs: Long = 0L
    private var pendingPlayJob: Job? = null
    private var pendingPlaySource: String? = null
    private var pendingPlayLabel: String? = null

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
        val prefs = buttonPreferences.state.value
        mutex.withLock {
            cancelPendingPlayLocked()
            lastGestureAtMs = 0L
            lastCommittedKey = BT_NEXT_KEY
            lastCommittedAtMs = now
            // Drop companion MEDIA_PLAY that Pixel Buds often send with NEXT.
            suppressPlayUntilMs = now + prefs.nextDoubleTapMs
        }
        logger.i(TAG, "$kind: $label via $source → Next (suppress Play ${prefs.nextDoubleTapMs}ms)")
        headsetTestController.recordBtNextEvent(
            source = source,
            kind = kind,
            viaDoublePlay = false,
        )
        if (!btPlayTestIsolation) {
            englishTutorPlayHandler.onMediaButton("MEDIA_NEXT", source)
        }
    }

    private suspend fun handlePlayGesture(label: String, source: String, now: Long) {
        val prefs = buttonPreferences.state.value
        val doubleTapMs = prefs.nextDoubleTapMs
        val kind = eventKind(source)

        val action = mutex.withLock {
            if (now < suppressPlayUntilMs) {
                PlayAction.SuppressedAfterNext
            } else if (
                pendingPlayJob != null &&
                lastGestureAtMs > 0L &&
                now - lastGestureAtMs <= doubleTapMs
            ) {
                cancelPendingPlayLocked()
                lastGestureAtMs = 0L
                lastCommittedKey = BT_NEXT_KEY
                lastCommittedAtMs = now
                suppressPlayUntilMs = now + doubleTapMs
                PlayAction.CommitNext
            } else if (
                prefs.debounceEnabled &&
                (lastCommittedKey == BT_PLAY_KEY || lastCommittedKey == BT_NEXT_KEY) &&
                now - lastCommittedAtMs < prefs.debounceIntervalMs
            ) {
                PlayAction.Debounce
            } else {
                lastGestureAtMs = now
                pendingPlaySource = source
                pendingPlayLabel = label
                val generation = ++pendingGeneration
                pendingPlayJob = scope.launch {
                    delay(doubleTapMs)
                    val commit = mutex.withLock {
                        if (generation != pendingGeneration || pendingPlaySource == null) {
                            null
                        } else {
                            val commitSource = pendingPlaySource!!
                            val commitLabel = pendingPlayLabel!!
                            pendingPlayJob = null
                            pendingPlaySource = null
                            pendingPlayLabel = null
                            lastCommittedKey = BT_PLAY_KEY
                            lastCommittedAtMs = System.currentTimeMillis()
                            commitLabel to commitSource
                        }
                    }
                    if (commit != null) {
                        dispatchPlay(commit.first, commit.second)
                    }
                }
                PlayAction.WaitForDouble(doubleTapMs)
            }
        }

        when (action) {
            PlayAction.Debounce -> {
                logger.d(TAG, "Debounced: $label ($source)")
            }
            PlayAction.SuppressedAfterNext -> {
                logger.d(TAG, "Suppressed Play after Next: $label ($source)")
            }
            PlayAction.CommitNext -> {
                logger.i(
                    TAG,
                    "$kind: double gesture ($label) via $source → Next (window ${prefs.nextDoubleTapMs}ms)",
                )
                headsetTestController.recordBtNextEvent(source = source, kind = kind, viaDoublePlay = true)
                if (!btPlayTestIsolation) {
                    englishTutorPlayHandler.onMediaButton("MEDIA_NEXT", source)
                }
            }
            is PlayAction.WaitForDouble -> {
                logger.d(TAG, "Play pending (${action.windowMs}ms) for double-tap: $label ($source)")
            }
        }
    }

    private var pendingGeneration: Int = 0

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

    private fun cancelPendingPlayLocked() {
        pendingGeneration++
        pendingPlayJob?.cancel()
        pendingPlayJob = null
        pendingPlaySource = null
        pendingPlayLabel = null
    }

    private sealed class PlayAction {
        data object Debounce : PlayAction()
        data object SuppressedAfterNext : PlayAction()
        data object CommitNext : PlayAction()
        data class WaitForDouble(val windowMs: Long) : PlayAction()
    }

    companion object {
        private const val TAG = "Headset"
        private const val BT_PLAY_KEY = "BT_PLAY"
        private const val BT_NEXT_KEY = "BT_NEXT"

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
