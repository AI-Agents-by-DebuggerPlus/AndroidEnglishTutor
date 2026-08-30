package com.englishtutor.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class BluetoothConnectionMonitor @Inject constructor(
    private val deviceHelper: BluetoothDeviceHelper,
    private val textToSpeech: TextToSpeechProvider,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()
    private var receiver: BluetoothAclReceiver? = null
    private var registrationCount = 0

    private var lastLoggedFingerprint: String? = null

    private val _snapshot = MutableStateFlow(
        BluetoothConnectionSnapshot(permissionGranted = false, devices = emptyList()),
    )
    val snapshot: StateFlow<BluetoothConnectionSnapshot> = _snapshot.asStateFlow()

    fun refresh(context: Context) {
        val next = deviceHelper.snapshot(context.applicationContext)
        val fingerprint = next.logFingerprint()
        if (fingerprint != lastLoggedFingerprint) {
            lastLoggedFingerprint = fingerprint
            logSnapshot(next)
        }
        _snapshot.value = next
    }

    private fun logSnapshot(snapshot: BluetoothConnectionSnapshot) {
        if (!snapshot.permissionGranted) {
            logger.i(TAG, "BT connected: permission not granted")
            logger.i(TAG, "BT active: permission not granted")
            return
        }
        if (snapshot.devices.isEmpty()) {
            logger.i(TAG, "BT connected: none")
        } else {
            val lines = snapshot.devices.joinToString(separator = "; ") { it.displayLine() }
            logger.i(TAG, "BT connected (${snapshot.devices.size}): $lines")
        }
        val active = snapshot.activeDevice
        if (active == null) {
            logger.i(TAG, "BT active: none")
        } else {
            logger.i(TAG, "BT active: ${active.displayLine()}")
        }
    }

    fun ensureStarted(context: Context) {
        synchronized(lock) {
            val appContext = context.applicationContext
            refresh(appContext)
            if (receiver != null) {
                registrationCount++
                return
            }
            receiver = BluetoothAclReceiver(
                deviceHelper = deviceHelper,
                targetNameHint = BluetoothConnectionConstants.DEFAULT_HEADSET_NAME_HINT,
                onAnnounce = { phrase ->
                    scope.launch {
                        logger.i(TAG, "ACL announce: $phrase")
                        runCatching {
                            textToSpeech.speak(phrase, "en-US")
                        }.onFailure { error ->
                            logger.w(TAG, "ACL announce failed: ${error.message}")
                        }
                    }
                },
                onEvent = {
                    refresh(appContext)
                    val snapshot = _snapshot.value
                    logger.i(
                        TAG,
                        "ACL event → connected=${snapshot.statusLabel}, active=${snapshot.activeStatusLabel}",
                    )
                },
            )
            appContext.registerReceiver(receiver, BluetoothAclReceiver.intentFilter())
            registrationCount = 1
            logger.i(TAG, "ACL receiver registered")
        }
    }

    fun stop(context: Context) {
        synchronized(lock) {
            if (receiver == null) {
                return
            }
            registrationCount--
            if (registrationCount > 0) {
                return
            }
            runCatching {
                context.applicationContext.unregisterReceiver(receiver)
            }.onFailure { error ->
                logger.w(TAG, "unregisterReceiver failed: ${error.message}")
            }
            receiver = null
            registrationCount = 0
            logger.i(TAG, "ACL receiver unregistered")
        }
    }

    private class BluetoothAclReceiver(
        private val deviceHelper: BluetoothDeviceHelper,
        private val targetNameHint: String,
        private val onAnnounce: (String) -> Unit,
        private val onEvent: () -> Unit,
    ) : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getBluetoothDeviceExtra() ?: return
            val connected = when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> true
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
                else -> return
            }

            if (deviceHelper.matchesTarget(device, targetNameHint)) {
                val phrase = if (connected) {
                    "$targetNameHint connected"
                } else {
                    "$targetNameHint disconnected"
                }
                onAnnounce(phrase)
            }

            onEvent()
        }

        private fun Intent.getBluetoothDeviceExtra(): BluetoothDevice? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

        companion object {
            fun intentFilter(): IntentFilter =
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }
        }
    }

    companion object {
        private const val TAG = "Bluetooth"
    }
}
