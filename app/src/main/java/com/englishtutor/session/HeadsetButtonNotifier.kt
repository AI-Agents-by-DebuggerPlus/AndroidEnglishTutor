package com.englishtutor.session

import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Routes headset media buttons — AndroidChat HeadsetButtonNotifier pattern.
 */
@Singleton
class HeadsetButtonNotifier @Inject constructor(
    private val headsetTestController: HeadsetTestController,
    private val englishTutorPlayHandler: EnglishTutorPlayHandler,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var lastSentKey: String? = null
    private var lastSentAtMs: Long = 0L

    @Volatile
    var btPlayTestIsolation: Boolean = false

    @Volatile
    var isolatedBtPlayHandler: (() -> Unit)? = null

    fun notifyButton(buttonLabel: String, source: String = "native") {
        val label = HeadsetButtonNames.normalize(buttonLabel)
        val now = System.currentTimeMillis()
        val debounceKey = if (HeadsetButtonNames.isBtPlayLabel(label)) BT_PLAY_KEY else label

        scope.launch {
            mutex.withLock {
                if (debounceKey == lastSentKey && now - lastSentAtMs < DEBOUNCE_MS) {
                    logger.d(TAG, "Debounced: $label ($source)")
                    return@launch
                }
                lastSentKey = debounceKey
                lastSentAtMs = now
            }

            if (HeadsetButtonNames.isBtPlayLabel(label)) {
                headsetTestController.recordBtPlayEvent(label)
                if (btPlayTestIsolation) {
                    val handler = isolatedBtPlayHandler
                    if (handler != null) {
                        logger.i(TAG, "BT Play ($label) via $source → isolated handler")
                        withContext(Dispatchers.Main) { handler() }
                    } else {
                        logger.i(TAG, "BT Play ($label) via $source → isolation (counter only)")
                    }
                    return@launch
                }
                logger.i(TAG, "BT Play ($label) via $source → lesson handler")
                englishTutorPlayHandler.handleBtPlay(source)
                return@launch
            }

            if (btPlayTestIsolation) {
                logger.d(TAG, "Non-play button ignored in isolation: $label ($source)")
                return@launch
            }

            englishTutorPlayHandler.onMediaButton(label, source)
        }
    }

    companion object {
        private const val TAG = "Headset"
        private const val DEBOUNCE_MS = 500L
        private const val BT_PLAY_KEY = "BT_PLAY"
    }
}
