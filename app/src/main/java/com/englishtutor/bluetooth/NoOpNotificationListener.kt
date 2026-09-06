package com.englishtutor.bluetooth

import android.service.notification.NotificationListenerService

/**
 * No-op listener — required only for MediaSessionManager.getActiveSessions()
 * via Notification Access (BtTest93 pattern).
 */
class NoOpNotificationListener : NotificationListenerService()
