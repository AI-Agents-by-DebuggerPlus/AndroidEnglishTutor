package com.englishtutor.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

data class ConnectedBluetoothDevice(
    val name: String,
    val address: String,
    val profileLabel: String,
) {
    fun displayLine(): String = "$name · $profileLabel · $address"
}

data class ActiveBluetoothDevice(
    val name: String,
    val address: String,
    val routeLabel: String,
) {
    fun displayLine(): String = "$name · $routeLabel · $address"
}

data class BluetoothConnectionSnapshot(
    val permissionGranted: Boolean,
    val devices: List<ConnectedBluetoothDevice>,
    val activeDevice: ActiveBluetoothDevice? = null,
) {
    val primaryDeviceName: String?
        get() = devices.firstOrNull()?.name

    val statusLabel: String
        get() = when {
            !permissionGranted -> "Нет разрешения Bluetooth"
            devices.isEmpty() -> "Гарнитура не подключена"
            devices.size == 1 -> devices.first().name
            else -> "${devices.first().name} (+${devices.size - 1})"
        }

    val activeStatusLabel: String
        get() = when {
            !permissionGranted -> "Нет разрешения Bluetooth"
            activeDevice == null -> "Активное устройство не определено"
            else -> activeDevice.name
        }

    val greetingPhrase: String
        get() = when {
            !permissionGranted -> "Bluetooth permission not granted"
            devices.isEmpty() -> "No Bluetooth headset connected"
            devices.size == 1 -> "${devices.first().name} connected"
            else -> "${devices.size} Bluetooth audio devices connected"
        }

    fun logFingerprint(): String =
        buildString {
            append("perm=$permissionGranted")
            append("|connected=")
            append(devices.joinToString(",") { "${it.address}:${it.profileLabel}" })
            append("|active=")
            append(activeDevice?.let { "${it.address}:${it.routeLabel}" }.orEmpty())
        }
}

@Singleton
class BluetoothDeviceHelper @Inject constructor(
    private val logger: AppLogger,
) {
    fun snapshot(context: Context): BluetoothConnectionSnapshot {
        if (!BluetoothPermissionHelper.hasConnectPermission(context)) {
            logger.w(TAG, "BLUETOOTH_CONNECT not granted")
            return BluetoothConnectionSnapshot(permissionGranted = false, devices = emptyList())
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            logger.i(TAG, "Bluetooth adapter unavailable or disabled")
            return BluetoothConnectionSnapshot(permissionGranted = true, devices = emptyList())
        }

        val seen = LinkedHashSet<String>()
        val devices = mutableListOf<ConnectedBluetoothDevice>()

        for ((profile, label) in PROFILE_LABELS) {
            for (device in connectedDevicesForProfile(bluetoothManager, profile)) {
                addDevice(devices, seen, device, label)
            }
        }

        if (devices.isEmpty()) {
            devices += audioManagerBluetoothDevices(context)
        }

        val activeDevice = resolveActiveDevice(context, devices)
        return BluetoothConnectionSnapshot(
            permissionGranted = true,
            devices = devices,
            activeDevice = activeDevice,
        )
    }

    fun matchesTarget(device: BluetoothDevice?, targetNameContains: String): Boolean {
        if (device == null || targetNameContains.isBlank()) {
            return false
        }
        val name = deviceName(device) ?: return false
        return name.contains(targetNameContains, ignoreCase = true)
    }

    private fun addDevice(
        devices: MutableList<ConnectedBluetoothDevice>,
        seen: LinkedHashSet<String>,
        device: BluetoothDevice,
        profileLabel: String,
    ) {
        val name = deviceName(device) ?: return
        val key = "${device.address}|$name"
        if (seen.add(key)) {
            devices += ConnectedBluetoothDevice(
                name = name,
                address = device.address,
                profileLabel = profileLabel,
            )
        }
    }

    private fun audioManagerBluetoothDevices(context: Context): List<ConnectedBluetoothDevice> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return emptyList()

        val deviceInfos = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { info ->
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }

        return deviceInfos.mapNotNull { info ->
            val name = info.productName?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                null
            } else {
                ConnectedBluetoothDevice(
                    name = name,
                    address = "audio:${info.id}",
                    profileLabel = "AUDIO",
                )
            }
        }
    }

    private fun connectedDevicesForProfile(
        bluetoothManager: BluetoothManager,
        profile: Int,
    ): List<BluetoothDevice> =
        try {
            bluetoothManager.getConnectedDevices(profile)
        } catch (error: SecurityException) {
            logger.w(TAG, "getConnectedDevices denied for profile=$profile")
            emptyList()
        } catch (error: IllegalArgumentException) {
            logger.w(TAG, "Profile $profile unavailable")
            emptyList()
        }

    private fun deviceName(device: BluetoothDevice): String? =
        try {
            device.name?.trim()?.takeIf { it.isNotEmpty() }
        } catch (error: SecurityException) {
            logger.w(TAG, "device.name denied")
            null
        }

    private fun resolveActiveDevice(
        context: Context,
        connected: List<ConnectedBluetoothDevice>,
    ): ActiveBluetoothDevice? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return singleConnectedFallback(connected)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice
                ?.let(::fromAudioDeviceInfo)
                ?.let { return it.copy(routeLabel = "COMMUNICATION") }
        }

        if (audioManager.isBluetoothScoOn) {
            findOutputDevice(audioManager, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                ?.let(::fromAudioDeviceInfo)
                ?.let { return it.copy(routeLabel = "SCO") }
            connected.firstOrNull { it.profileLabel == "HEADSET" }
                ?.let { return ActiveBluetoothDevice(it.name, it.address, "SCO") }
        }

        if (audioManager.isBluetoothA2dpOn) {
            findOutputDevice(audioManager, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                ?.let(::fromAudioDeviceInfo)
                ?.let { return it.copy(routeLabel = "A2DP") }
            connected.firstOrNull { it.profileLabel == "A2DP" }
                ?.let { return ActiveBluetoothDevice(it.name, it.address, "A2DP") }
        }

        val routedBluetooth = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { info ->
                info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    info.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        info.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            ?.let(::fromAudioDeviceInfo)

        return routedBluetooth ?: singleConnectedFallback(connected)
    }

    private fun singleConnectedFallback(
        connected: List<ConnectedBluetoothDevice>,
    ): ActiveBluetoothDevice? {
        if (connected.size != 1) {
            return null
        }
        val device = connected.first()
        return ActiveBluetoothDevice(
            name = device.name,
            address = device.address,
            routeLabel = device.profileLabel,
        )
    }

    private fun findOutputDevice(
        audioManager: AudioManager,
        type: Int,
    ): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == type }

    private fun fromAudioDeviceInfo(info: AudioDeviceInfo): ActiveBluetoothDevice? {
        val name = info.productName?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            return null
        }
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { info.address }.getOrNull().orEmpty()
                .ifBlank { "audio:${info.id}" }
        } else {
            "audio:${info.id}"
        }
        return ActiveBluetoothDevice(
            name = name,
            address = address,
            routeLabel = audioTypeLabel(info.type),
        )
    }

    private fun audioTypeLabel(type: Int): String =
        when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "SCO"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE"
            else -> "AUDIO"
        }

    companion object {
        private const val TAG = "Bluetooth"

        private val PROFILE_LABELS = listOf(
            BluetoothProfile.HEADSET to "HEADSET",
            BluetoothProfile.A2DP to "A2DP",
        )
    }
}
