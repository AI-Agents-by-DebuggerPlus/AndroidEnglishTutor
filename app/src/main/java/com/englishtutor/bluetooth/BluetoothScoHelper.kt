package com.englishtutor.bluetooth

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.englishtutor.session.MediaPlaybackPulse
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class BluetoothAudioRouteSnapshot(
    val mode: Int,
    val scoOn: Boolean,
    val a2dpOn: Boolean,
    val communicationDevice: String?,
    val a2dpOutputs: List<String>,
    val scoOutputs: List<String>,
) {
    val modeLabel: String
        get() = when (mode) {
            AudioManager.MODE_NORMAL -> "NORMAL"
            AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
            AudioManager.MODE_IN_CALL -> "IN_CALL"
            AudioManager.MODE_RINGTONE -> "RINGTONE"
            else -> "mode=$mode"
        }

    /** Strict: safe to start USAGE_MEDIA TTS toward headset (not phone earpiece/speaker). */
    val readyForMediaTts: Boolean
        get() = !scoOn &&
            mode == AudioManager.MODE_NORMAL &&
            a2dpOn &&
            a2dpOutputs.isNotEmpty() &&
            !isPhoneCommunicationDevice

    /** Phone earpiece/speaker or SCO still claimed as communication device. */
    val isPhoneCommunicationDevice: Boolean
        get() {
            val c = communicationDevice ?: return false
            return c.startsWith("EARPIECE", ignoreCase = true) ||
                c.startsWith("SPEAKER", ignoreCase = true) ||
                c.contains("SCO", ignoreCase = true) ||
                c.contains("BUILTIN", ignoreCase = true)
        }

    fun compact(): String =
        "mode=$modeLabel sco=$scoOn a2dpOn=$a2dpOn comm=${communicationDevice ?: "none"} " +
            "a2dpOut=${a2dpOutputs.joinToString("|").ifBlank { "—" }} " +
            "scoOut=${scoOutputs.joinToString("|").ifBlank { "—" }}"
}

/**
 * Routes communication audio (STT) to Bluetooth SCO and restores A2DP before media TTS.
 */
@Singleton
class BluetoothScoHelper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val mediaPlaybackPulse: MediaPlaybackPulse,
    private val logger: AppLogger,
) {
    @Volatile
    private var scoEnabled = false

    @Volatile
    private var lastScoStopAtMs: Long = 0L

    fun snapshot(): BluetoothAudioRouteSnapshot {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
            ?: return BluetoothAudioRouteSnapshot(
                mode = AudioManager.MODE_NORMAL,
                scoOn = false,
                a2dpOn = false,
                communicationDevice = null,
                a2dpOutputs = emptyList(),
                scoOutputs = emptyList(),
            )
        return snapshot(audioManager)
    }

    fun isReadyForMediaTts(): Boolean = snapshot().readyForMediaTts

    suspend fun enableAndWait(timeoutMs: Long = SCO_TIMEOUT_MS): Boolean = withContext(Dispatchers.Main) {
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return@withContext false
        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val scoDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (scoDevice != null) {
                    val ok = audioManager.setCommunicationDevice(scoDevice)
                    logger.i(TAG, "SCO setCommunicationDevice ok=$ok · ${snapshot(audioManager).compact()}")
                    if (ok) {
                        scoEnabled = true
                        delay(200)
                        return@withContext true
                    }
                } else {
                    logger.w(TAG, "No BT_SCO communication device")
                }
            }

            if (!audioManager.isBluetoothScoAvailableOffCall) {
                logger.w(TAG, "Bluetooth SCO not available off-call")
                return@withContext false
            }

            if (!audioManager.isBluetoothScoOn) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                logger.i(TAG, "SCO start requested")
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (audioManager.isBluetoothScoOn) {
                    scoEnabled = true
                    logger.i(TAG, "SCO connected · ${snapshot(audioManager).compact()}")
                    delay(150)
                    return@withContext true
                }
                delay(100)
            }

            scoEnabled = audioManager.isBluetoothScoOn
            logger.w(TAG, "SCO wait timed out · ${snapshot(audioManager).compact()}")
            scoEnabled
        }.getOrElse { error ->
            logger.w(TAG, "SCO enable failed: ${error.message}")
            false
        }
    }

    fun disable() {
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            if (scoEnabled || audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            if (scoEnabled) {
                lastScoStopAtMs = System.currentTimeMillis()
                logger.i(TAG, "SCO stopped · ${snapshot(audioManager).compact()}")
            }
            scoEnabled = false
        }.onFailure { error ->
            logger.w(TAG, "SCO disable failed: ${error.message}")
        }
    }

    /**
     * Tear down SCO, wait until A2DP media route is actually usable, then nudge
     * USAGE_MEDIA so the system prefers headset over phone speaker for TTS.
     */
    suspend fun disableAndAwaitMedia(
        minSettleMs: Long = MEDIA_MIN_SETTLE_MS,
        timeoutMs: Long = MEDIA_TIMEOUT_MS,
    ): Boolean = withContext(Dispatchers.Main) {
        disable()
        val ready = awaitA2dpForMediaTts(minSettleMs = minSettleMs, timeoutMs = timeoutMs)
        if (ready) {
            logger.i(TAG, "A2DP ready — USAGE_MEDIA pulse before TTS")
            withContext(Dispatchers.IO) {
                mediaPlaybackPulse.pulse()
            }
            delay(POST_PULSE_MS)
            val after = snapshot()
            logger.i(TAG, "Post-pulse route · ${after.compact()} ready=${after.readyForMediaTts}")
            return@withContext after.readyForMediaTts
        }
        logger.w(TAG, "A2DP not ready — TTS may go to phone speaker · ${snapshot().compact()}")
        false
    }

    suspend fun awaitA2dpForMediaTts(
        minSettleMs: Long = MEDIA_MIN_SETTLE_MS,
        timeoutMs: Long = MEDIA_TIMEOUT_MS,
    ): Boolean = withContext(Dispatchers.Main) {
        val audioManager = appContext.getSystemService(AudioManager::class.java)
            ?: return@withContext false

        val sinceSco = System.currentTimeMillis() - lastScoStopAtMs
        val remainingMin = (minSettleMs - sinceSco).coerceAtLeast(0L)
        if (remainingMin > 0L) {
            logger.i(TAG, "A2DP wait minSettle ${remainingMin}ms (after SCO)")
            delay(remainingMin)
        }

        // Fast path only after forcing clear of phone communication device.
        forceMediaRouteDefaults(audioManager)
        val first = snapshot(audioManager)
        if (first.readyForMediaTts) {
            logger.i(TAG, "A2DP already ready · ${first.compact()}")
            return@withContext true
        }
        if (first.isPhoneCommunicationDevice) {
            logger.i(TAG, "Phone comm still set — will wait · ${first.compact()}")
        }
        logger.i(TAG, "A2DP wait start · ${first.compact()}")

        val deadline = System.currentTimeMillis() + timeoutMs
        var lastLoggedPhoneCommAt = 0L
        while (System.currentTimeMillis() < deadline) {
            forceMediaRouteDefaults(audioManager)
            val snap = snapshot(audioManager)
            if (snap.isPhoneCommunicationDevice && System.currentTimeMillis() - lastLoggedPhoneCommAt > 500L) {
                logger.i(TAG, "Waiting: clear phone comm device · ${snap.compact()}")
                lastLoggedPhoneCommAt = System.currentTimeMillis()
            }
            if (snap.readyForMediaTts) {
                delay(EXTRA_STABLE_MS)
                forceMediaRouteDefaults(audioManager)
                val stable = snapshot(audioManager)
                if (stable.readyForMediaTts) {
                    logger.i(TAG, "A2DP restored · ${stable.compact()}")
                    return@withContext true
                }
            }
            delay(150)
        }
        logger.w(TAG, "A2DP wait timed out · ${snapshot(audioManager).compact()}")
        false
    }

    private fun forceMediaRouteDefaults(audioManager: AudioManager) {
        if (audioManager.mode != AudioManager.MODE_NORMAL) {
            audioManager.mode = AudioManager.MODE_NORMAL
        }
        @Suppress("DEPRECATION")
        if (audioManager.isSpeakerphoneOn) {
            audioManager.isSpeakerphoneOn = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val comm = audioManager.communicationDevice
            if (comm != null) {
                val type = comm.type
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                    type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                ) {
                    audioManager.clearCommunicationDevice()
                }
            }
        }
        if (audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    private fun snapshot(audioManager: AudioManager): BluetoothAudioRouteSnapshot {
        val outputs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        } else {
            emptyList()
        }
        val a2dpNames = outputs
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            .map { it.productName?.toString() ?: "a2dp" }
        val scoNames = outputs
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            .map { it.productName?.toString() ?: "sco" }
        val comm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { device ->
                "${deviceTypeLabel(device.type)}:${device.productName}"
            }
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val a2dpOn = audioManager.isBluetoothA2dpOn
        return BluetoothAudioRouteSnapshot(
            mode = audioManager.mode,
            scoOn = audioManager.isBluetoothScoOn,
            a2dpOn = a2dpOn,
            communicationDevice = comm,
            a2dpOutputs = a2dpNames,
            scoOutputs = scoNames,
        )
    }

    private fun deviceTypeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "SCO"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        else -> "type$type"
    }

    companion object {
        private const val TAG = "BtAudio"
        private const val SCO_TIMEOUT_MS = 2_500L
        /** Device list alone is not enough; A2DP profile needs time after SCO. */
        private const val MEDIA_MIN_SETTLE_MS = 1_000L
        private const val MEDIA_TIMEOUT_MS = 4_000L
        private const val EXTRA_STABLE_MS = 250L
        private const val POST_PULSE_MS = 200L
    }
}
