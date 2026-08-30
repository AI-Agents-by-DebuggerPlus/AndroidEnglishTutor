package com.englishtutor

import android.app.Application
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.session.HeadsetMonitorService
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EnglishTutorApp : Application() {

    @Inject lateinit var logger: AppLogger
    @Inject lateinit var bluetoothConnectionMonitor: BluetoothConnectionMonitor

    override fun onCreate() {
        super.onCreate()
        logger.i("App", "Started · ${AppVersion.label}")
        bluetoothConnectionMonitor.ensureStarted(this)
        runCatching {
            HeadsetMonitorService.start(this)
        }.onFailure { error ->
            logger.e("App", "HeadsetMonitorService failed to start: ${error.message}")
        }
    }
}
