package com.englishtutor.bluetooth

import android.content.Context
import android.media.AudioManager
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothScoHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val logger: AppLogger,
) {
    private var scoEnabled = false

    fun enable() {
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                scoEnabled = true
                logger.i(TAG, "Bluetooth SCO started")
            }
        }.onFailure { error ->
            logger.w(TAG, "SCO enable failed: ${error.message}")
        }
    }

    fun disable() {
        if (!scoEnabled) {
            return
        }
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            scoEnabled = false
            logger.i(TAG, "Bluetooth SCO stopped")
        }.onFailure { error ->
            logger.w(TAG, "SCO disable failed: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "Bluetooth"
    }
}
