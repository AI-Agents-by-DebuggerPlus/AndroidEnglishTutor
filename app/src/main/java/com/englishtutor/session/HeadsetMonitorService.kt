package com.englishtutor.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.englishtutor.MainActivity
import com.englishtutor.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Sticky foreground MediaSession for Bluetooth headset buttons (AndroidChat pattern).
 * No audio focus, no MediaButtonReceiver — plain notification + active session.
 */
@AndroidEntryPoint
class HeadsetMonitorService : Service() {

    @Inject lateinit var headsetButtonNotifier: HeadsetButtonNotifier

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        attachMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaSession?.isActive != true) {
            attachMediaSession()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attachMediaSession() {
        if (mediaSession != null) {
            mediaSession?.isActive = true
            return
        }

        val notifier = headsetButtonNotifier
        val session = MediaSessionCompat(this, "EnglishTutorHeadset").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        notifier.notifyButton("MEDIA_PLAY")
                    }

                    override fun onPause() {
                        notifier.notifyButton("MEDIA_PAUSE")
                    }

                    override fun onSkipToNext() {
                        notifier.notifyButton("MEDIA_NEXT")
                    }

                    override fun onSkipToPrevious() {
                        notifier.notifyButton("MEDIA_PREVIOUS")
                    }

                    override fun onStop() {
                        notifier.notifyButton("MEDIA_STOP")
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent?): Boolean {
                        val event = extractKeyEvent(mediaButtonIntent)
                            ?: return super.onMediaButtonEvent(mediaButtonIntent)
                        val label = HeadsetButtonNames.fromKeyCode(event.keyCode)
                        if (label != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            notifier.notifyButton(label)
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                },
            )
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP,
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                    .build(),
            )
            isActive = true
        }
        mediaSession = session
    }

    private fun extractKeyEvent(intent: Intent?): KeyEvent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Кнопки гарнитуры",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Мониторинг media-кнопок Bluetooth"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.headset_test_notification_title))
            .setContentText(getString(R.string.headset_test_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "headset_monitor"
        private const val NOTIFICATION_ID = 43

        fun start(context: Context) {
            val intent = Intent(context, HeadsetMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeadsetMonitorService::class.java))
        }
    }
}
