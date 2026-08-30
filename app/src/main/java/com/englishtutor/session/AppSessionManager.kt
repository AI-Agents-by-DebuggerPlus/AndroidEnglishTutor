package com.englishtutor.session

import android.content.Context
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.bluetooth.BluetoothDeviceHelper
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AppSessionManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val textToSpeech: TextToSpeechProvider,
    private val bluetoothDeviceHelper: BluetoothDeviceHelper,
    private val bluetoothConnectionMonitor: BluetoothConnectionMonitor,
    private val logger: AppLogger,
) {
    private val greetingMutex = Mutex()
    private val stopMutex = Mutex()
    private var greetingPlayed = false
    private var stopInProgress = false

    var onExit: (() -> Unit)? = null

    suspend fun playStartupGreetingIfNeeded() = greetingMutex.withLock {
        if (greetingPlayed) {
            return@withLock
        }
        greetingPlayed = true
        bluetoothConnectionMonitor.ensureStarted(appContext)
        val bt = bluetoothDeviceHelper.snapshot(appContext)
        logger.i("App", "Startup greeting · ${AppVersion.label} · BT=${bt.statusLabel}")
        try {
            textToSpeech.speak(STARTUP_GREETING, "en-US")
            textToSpeech.speak(bt.greetingPhrase, "en-US")
        } catch (error: Exception) {
            logger.e("App", "Startup greeting failed: ${error.message}")
        }
    }

    suspend fun stopApp() = stopMutex.withLock {
        if (stopInProgress) {
            return@withLock
        }
        stopInProgress = true
        logger.i("App", "Stop requested")
        try {
            textToSpeech.speak(STOP_PHRASE, "en-US")
        } catch (error: Exception) {
            logger.e("App", "Stop phrase failed: ${error.message}")
        }
        shutdownBackgroundWork()
        onExit?.invoke()
    }

    private fun shutdownBackgroundWork() {
        runCatching { HeadsetMonitorService.stop(appContext) }
        runCatching {
            appContext.startService(
                android.content.Intent(appContext, LessonSessionService::class.java).apply {
                    action = LessonSessionService.ACTION_STOP
                },
            )
        }
        runCatching { bluetoothConnectionMonitor.stop(appContext) }
        logger.i("App", "Background work stopped")
    }

    companion object {
        const val STARTUP_GREETING = "Android English Tutor is ready"
        const val STOP_PHRASE = "AndroidEnglishTutor will be stopped"
    }
}
