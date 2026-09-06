package com.englishtutor.bluetooth

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.englishtutor.session.HeadsetMonitorService
import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticLevel {
    OK,
    WARN,
    FAIL,
}

data class HeadsetDiagnosticLine(
    val id: String,
    val label: String,
    val detail: String,
    val level: DiagnosticLevel,
) {
    fun logLine(): String = "${level.name} $label: $detail"
}

data class HeadsetDiagnosticsSnapshot(
    val lines: List<HeadsetDiagnosticLine>,
    val mediaButtonPathReady: Boolean,
    val notificationAccessEnabled: Boolean = false,
    val activeSessions: List<ActiveMediaSessionRow> = emptyList(),
) {
    val summary: String
        get() = when {
            mediaButtonPathReady && activeSessions.firstOrNull()?.isSelf == true ->
                "Готов: media-button #1 = AndEngTutor"
            mediaButtonPathReady && activeSessions.isNotEmpty() &&
                activeSessions.firstOrNull()?.isSelf != true ->
                "Сессия активна, но #1 = ${activeSessions.first().appLabel}"
            mediaButtonPathReady -> "Готов к приёму media-кнопок"
            lines.any { it.level == DiagnosticLevel.FAIL } -> "Есть блокирующие проблемы"
            else -> "Возможны ограничения"
        }
}

@Singleton
class HeadsetDiagnosticsHelper @Inject constructor(
    private val taskerConflictChecker: TaskerConflictChecker,
    private val activeSessionsHelper: ActiveSessionsHelper,
    private val logger: AppLogger,
) {
    private var lastLogFingerprint: String? = null

    fun collect(
        context: Context,
        nativeCaptureOn: Boolean,
        btIsolationOn: Boolean,
        micGranted: Boolean,
    ): HeadsetDiagnosticsSnapshot {
        val appContext = context.applicationContext
        val bluetoothConnect = BluetoothPermissionHelper.hasConnectPermission(appContext)
        val notificationsGranted = hasNotificationsPermission(appContext)
        val bluetoothEnabled = isBluetoothEnabled(appContext)
        val monitorRunning = isHeadsetMonitorRunning(appContext)
        val taskerMayConflict = taskerConflictChecker.taskerMayGrabMediaButtons(appContext)
        val notificationAccess = activeSessionsHelper.isNotificationAccessEnabled(appContext)
        val activeSessions = activeSessionsHelper.snapshot(appContext)
        val topIsSelf = activeSessions.firstOrNull()?.isSelf == true

        val lines = buildList {
            add(
                permissionLine(
                    id = "bluetooth_connect",
                    label = "BLUETOOTH_CONNECT",
                    granted = bluetoothConnect,
                    requiredForBtPlay = false,
                    detailGranted = "выдано — видны подключённые устройства",
                    detailDenied = "не выдано — список BT-устройств недоступен",
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    permissionLine(
                        id = "post_notifications",
                        label = "POST_NOTIFICATIONS",
                        granted = notificationsGranted,
                        requiredForBtPlay = true,
                        detailGranted = "выдано — foreground-сервис может работать стабильно",
                        detailDenied = "не выдано — HeadsetMonitorService могут ограничить",
                    ),
                )
            }
            add(
                permissionLine(
                    id = "record_audio",
                    label = "RECORD_AUDIO",
                    granted = micGranted,
                    requiredForBtPlay = false,
                    detailGranted = "выдано — STT доступен",
                    detailDenied = "не выдано — только BT Play / TTS",
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "bluetooth_adapter",
                    label = "Bluetooth адаптер",
                    detail = if (bluetoothEnabled) "включён" else "выключен в системе",
                    level = if (bluetoothEnabled) DiagnosticLevel.OK else DiagnosticLevel.FAIL,
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "headset_monitor_service",
                    label = "HeadsetMonitorService",
                    detail = if (monitorRunning) {
                        "запущен (foreground MediaSession)"
                    } else {
                        "не запущен — media-кнопки не принимаются"
                    },
                    level = if (monitorRunning) DiagnosticLevel.OK else DiagnosticLevel.FAIL,
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "media_session",
                    label = "MediaSession capture",
                    detail = if (nativeCaptureOn) {
                        "активна (Native capture ON)"
                    } else {
                        "не активна — дождитесь старта сервиса или откройте Tests снова"
                    },
                    level = if (nativeCaptureOn) DiagnosticLevel.OK else DiagnosticLevel.FAIL,
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "bt_isolation",
                    label = "Режим изоляции Tests",
                    detail = if (btIsolationOn) {
                        "ON — кнопки идут в счётчик BT Play"
                    } else {
                        "OFF — кнопки идут в урок"
                    },
                    level = if (btIsolationOn) DiagnosticLevel.OK else DiagnosticLevel.WARN,
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "tasker_listener",
                    label = "Tasker SET_MEDIA_KEY_LISTENER",
                    detail = if (taskerMayConflict) {
                        "выдано Tasker — возможен перехват Grab (BT Key)"
                    } else {
                        "не выдано Tasker — конфликт маловероятен"
                    },
                    level = if (taskerMayConflict) DiagnosticLevel.WARN else DiagnosticLevel.OK,
                ),
            )
            add(
                HeadsetDiagnosticLine(
                    id = "notification_access",
                    label = "Notification Access",
                    detail = if (notificationAccess) {
                        "выдано — виден список ActiveSessions"
                    } else {
                        "не выдано — откройте доступ, чтобы видеть кто #1"
                    },
                    level = if (notificationAccess) DiagnosticLevel.OK else DiagnosticLevel.WARN,
                ),
            )
            if (activeSessions.isEmpty()) {
                add(
                    HeadsetDiagnosticLine(
                        id = "media_button_owner",
                        label = "Media-button #1",
                        detail = if (notificationAccess) {
                            "нет active sessions"
                        } else {
                            "неизвестно (нужен Notification Access)"
                        },
                        level = DiagnosticLevel.WARN,
                    ),
                )
            } else {
                val top = activeSessions.first()
                add(
                    HeadsetDiagnosticLine(
                        id = "media_button_owner",
                        label = "Media-button #1",
                        detail = "${top.appLabel} (${top.packageName}) ${top.playbackState}" +
                            (top.competitorNote?.let { " — $it" }.orEmpty()),
                        level = when {
                            top.isSelf -> DiagnosticLevel.OK
                            top.isKnownCompetitor -> DiagnosticLevel.FAIL
                            else -> DiagnosticLevel.WARN
                        },
                    ),
                )
            }
            add(
                HeadsetDiagnosticLine(
                    id = "media_button_path",
                    label = "Путь media-кнопки",
                    detail = buildMediaButtonPathDetail(
                        monitorRunning = monitorRunning,
                        nativeCaptureOn = nativeCaptureOn,
                        taskerMayConflict = taskerMayConflict,
                        topIsSelf = topIsSelf,
                        hasSessionSnapshot = activeSessions.isNotEmpty(),
                    ),
                    level = mediaButtonPathLevel(
                        monitorRunning = monitorRunning,
                        nativeCaptureOn = nativeCaptureOn,
                        taskerMayConflict = taskerMayConflict,
                        topIsSelf = topIsSelf,
                        hasSessionSnapshot = activeSessions.isNotEmpty(),
                    ),
                ),
            )
        }

        val mediaButtonPathReady = monitorRunning &&
            nativeCaptureOn &&
            !taskerMayConflict &&
            bluetoothEnabled &&
            (!notificationAccess || topIsSelf || activeSessions.isEmpty())

        return HeadsetDiagnosticsSnapshot(
            lines = lines,
            mediaButtonPathReady = mediaButtonPathReady,
            notificationAccessEnabled = notificationAccess,
            activeSessions = activeSessions,
        ).also { snapshot ->
            logSnapshot(snapshot)
        }
    }

    private fun logSnapshot(snapshot: HeadsetDiagnosticsSnapshot) {
        val fingerprint = snapshot.lines.joinToString("|") { "${it.id}:${it.level}" }
        if (fingerprint == lastLogFingerprint) {
            return
        }
        lastLogFingerprint = fingerprint
        logger.i(TAG, "Diagnostics: ${snapshot.summary}")
        snapshot.lines.forEach { line ->
            when (line.level) {
                DiagnosticLevel.OK -> logger.d(TAG, line.logLine())
                DiagnosticLevel.WARN -> logger.w(TAG, line.logLine())
                DiagnosticLevel.FAIL -> logger.w(TAG, line.logLine())
            }
        }
    }

    private fun permissionLine(
        id: String,
        label: String,
        granted: Boolean,
        requiredForBtPlay: Boolean,
        detailGranted: String,
        detailDenied: String,
    ): HeadsetDiagnosticLine {
        val level = when {
            granted -> DiagnosticLevel.OK
            requiredForBtPlay -> DiagnosticLevel.FAIL
            else -> DiagnosticLevel.WARN
        }
        return HeadsetDiagnosticLine(
            id = id,
            label = label,
            detail = if (granted) detailGranted else detailDenied,
            level = level,
        )
    }

    private fun buildMediaButtonPathDetail(
        monitorRunning: Boolean,
        nativeCaptureOn: Boolean,
        taskerMayConflict: Boolean,
        topIsSelf: Boolean,
        hasSessionSnapshot: Boolean,
    ): String =
        when {
            taskerMayConflict ->
                "Headset → Tasker (Grab?) → … — AndEngTutor может не получить Play"
            !monitorRunning || !nativeCaptureOn ->
                "Headset → … → HeadsetMonitorService (не готов)"
            hasSessionSnapshot && !topIsSelf ->
                "Headset → чужая MediaSession #1 — нажмите Reassert / закройте конкурента"
            else ->
                "Headset → AVRCP → HeadsetMonitorService → HeadsetButtonNotifier → счётчик"
        }

    private fun mediaButtonPathLevel(
        monitorRunning: Boolean,
        nativeCaptureOn: Boolean,
        taskerMayConflict: Boolean,
        topIsSelf: Boolean,
        hasSessionSnapshot: Boolean,
    ): DiagnosticLevel =
        when {
            taskerMayConflict -> DiagnosticLevel.WARN
            hasSessionSnapshot && !topIsSelf -> DiagnosticLevel.FAIL
            monitorRunning && nativeCaptureOn -> DiagnosticLevel.OK
            else -> DiagnosticLevel.FAIL
        }

    private fun hasNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isBluetoothEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    private fun isHeadsetMonitorRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == HeadsetMonitorService::class.java.name }
    }

    companion object {
        private const val TAG = "HeadsetDiag"
    }
}
