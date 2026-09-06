package com.englishtutor.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.englishtutor.MainActivity
import com.englishtutor.R
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.util.AppLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Sticky FGS + MediaSession for BT media buttons (AndroidChatBtTest95 pattern).
 * AudioFocus + PLAYING→PAUSED claim + force reassert against YouTube/competitors.
 * See: Docs/Claude / BT_Play AndroidEnglishTutor_BT_Play_Test_Agent_Instruction.md
 */
@AndroidEntryPoint
class HeadsetMonitorService : Service() {

    @Inject lateinit var headsetButtonNotifier: HeadsetButtonNotifier
    @Inject lateinit var headsetTestController: HeadsetTestController
    @Inject lateinit var bluetoothConnectionMonitor: BluetoothConnectionMonitor
    @Inject lateinit var mediaPlaybackPulse: MediaPlaybackPulse
    @Inject lateinit var logger: AppLogger

    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pulseExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        attachMediaSessionOnMain(forceReattach = true)
        bluetoothConnectionMonitor.ensureStarted(this)
        bluetoothConnectionMonitor.setOnAclEventListener {
            attachMediaSessionOnMain(forceReattach = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val force = intent?.getBooleanExtra(EXTRA_FORCE_REASSERT, false) == true
        if (force) {
            attachMediaSessionOnMain(forceReattach = true)
        } else if (mediaSession?.isActive != true) {
            attachMediaSessionOnMain(forceReattach = mediaSession != null)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        bluetoothConnectionMonitor.setOnAclEventListener(null)
        headsetTestController.setCaptureStatus(nativeCaptureOn = false)
        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun attachMediaSessionOnMain(forceReattach: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            attachMediaSession(forceReattach)
        } else {
            mainHandler.post { attachMediaSession(forceReattach) }
        }
    }

    private fun attachMediaSession(forceReattach: Boolean = false) {
        if (mediaSession != null && !forceReattach) {
            mediaSession?.isActive = true
            headsetTestController.setCaptureStatus(nativeCaptureOn = true)
            return
        }
        if (forceReattach) {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        }

        requestAudioFocus()

        val notifier = headsetButtonNotifier
        val controller = headsetTestController
        val actions =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP

        val session = MediaSessionCompat(this, "AndEngTutorHeadset").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onPlay() {
                        notifier.notifyButton("MEDIA_PLAY", source = "hardware-callback-onPlay")
                    }

                    override fun onPause() {
                        notifier.notifyButton("MEDIA_PAUSE", source = "hardware-callback-onPause")
                    }

                    override fun onSkipToNext() {
                        notifier.notifyButton("MEDIA_NEXT", source = "hardware-callback-onNext")
                    }

                    override fun onSkipToPrevious() {
                        notifier.notifyButton("MEDIA_PREVIOUS", source = "hardware-callback-onPrev")
                    }

                    override fun onStop() {
                        notifier.notifyButton("MEDIA_STOP", source = "hardware-callback-onStop")
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent?): Boolean {
                        val event = extractKeyEvent(mediaButtonIntent)
                            ?: return super.onMediaButtonEvent(mediaButtonIntent)
                        val label = HeadsetButtonNames.fromKeyCode(event.keyCode)
                        if (label != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                            notifier.notifyButton(label, source = "hardware-mediaButtonEvent")
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                },
            )
            // Claim media-button session: brief PLAYING → PAUSED (BtTest95).
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            isActive = true
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                    .build(),
            )
        }
        mediaSession = session
        controller.setCaptureStatus(nativeCaptureOn = true)
        logger.i(TAG, "MediaSession attached force=$forceReattach")
        if (forceReattach) {
            // Claim media-button session under this app UID (TTS cue alone is Google TTS).
            pulseExecutor.execute {
                mediaPlaybackPulse.pulse()
                mainHandler.post { refreshClaimPlaybackState() }
            }
        }
    }

    private fun refreshClaimPlaybackState() {
        val session = mediaSession ?: return
        val actions =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .build(),
        )
        session.isActive = true
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
                .build(),
        )
        logger.i(TAG, "MediaSession claim refreshed after USAGE_MEDIA pulse")
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = req
        val result = am.requestAudioFocus(req)
        logger.i(TAG, "AudioFocus request result=$result")
    }

    private fun abandonAudioFocus() {
        val req = audioFocusRequest ?: return
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        am.abandonAudioFocusRequest(req)
        audioFocusRequest = null
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
        private const val TAG = "HeadsetMonitor"
        private const val CHANNEL_ID = "headset_monitor"
        private const val NOTIFICATION_ID = 43
        private const val EXTRA_FORCE_REASSERT = "force_reassert"

        fun start(context: Context) {
            val intent = Intent(context, HeadsetMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Force recreate MediaSession + AudioFocus (vs YouTube / other competitors). */
        fun reassert(context: Context) {
            val intent = Intent(context, HeadsetMonitorService::class.java)
                .putExtra(EXTRA_FORCE_REASSERT, true)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeadsetMonitorService::class.java))
        }
    }
}
