package com.englishtutor.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.englishtutor.MainActivity
import com.englishtutor.R
import com.englishtutor.domain.model.LessonPhase
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground MediaSession host so headset AVRCP buttons work with screen off / app backgrounded.
 */
@AndroidEntryPoint
class LessonSessionService : Service() {

    @Inject lateinit var sessionController: LessonSessionController
    @Inject lateinit var lessonRepository: LessonRepository
    @Inject lateinit var progressRepository: ProgressRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null

    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                hasAudioFocus = false
                sessionController.setAudioFocusHeld(false)
                // Keep session alive on transient loss (call, other media).
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                sessionController.setAudioFocusHeld(true)
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "EnglishTutorLesson").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    ensureAudioFocus()
                    sessionController.onPlayPause()
                }

                override fun onPause() {
                    ensureAudioFocus()
                    sessionController.onPlayPause()
                }

                override fun onSkipToNext() {
                    ensureAudioFocus()
                    sessionController.onNext()
                }

                override fun onSkipToPrevious() {
                    ensureAudioFocus()
                    sessionController.onPrevious()
                }
            })
            isActive = true
        }

        stateJob = serviceScope.launch {
            sessionController.state.collectLatest { state ->
                updateMetadata(state.lessonTitle, state.currentPhrase, state.phraseProgressLabel)
                val playbackState = when (state.phase) {
                    LessonPhase.SPEAKING_PHRASE,
                    LessonPhase.SPEAKING_FEEDBACK,
                    LessonPhase.RECORDING,
                    -> PlaybackStateCompat.STATE_PLAYING
                    LessonPhase.COMPLETED -> PlaybackStateCompat.STATE_STOPPED
                    else -> PlaybackStateCompat.STATE_PAUSED
                }
                updatePlaybackState(playbackState)
                updateNotification(
                    title = state.lessonTitle.ifBlank { getString(R.string.app_name) },
                    text = state.statusMessage ?: state.currentPhrase,
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            else -> {
                val lessonId = intent?.getStringExtra(EXTRA_LESSON_ID)
                if (lessonId != null) {
                    serviceScope.launch { startLesson(lessonId) }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startLesson(lessonId: String) {
        val lesson = lessonRepository.getLesson(lessonId) ?: run {
            stopSelf()
            return
        }
        val alreadyCompleted = progressRepository.isLessonCompleted(lessonId)
        val notification = buildNotification(lesson.title, "Сессия урока активна")
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

        ensureAudioFocus()
        sessionController.start(lesson = lesson, alreadyCompleted = alreadyCompleted)
    }

    private fun ensureAudioFocus() {
        if (hasAudioFocus) {
            sessionController.setAudioFocusHeld(true)
            return
        }
        val manager = audioManager ?: return
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        sessionController.setAudioFocusHeld(hasAudioFocus)
        mediaSession?.isActive = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        sessionController.setAudioFocusHeld(false)
    }

    private fun updatePlaybackState(state: Int) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
    }

    private fun updateMetadata(title: String, phrase: String, progress: String) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, phrase.ifBlank { title })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "English Tutor")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "$title · $progress")
                .build(),
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lesson session",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps headset controls active during a lesson"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LessonSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken),
            )
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun stopSession() {
        sessionController.stop()
        abandonAudioFocus()
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.cancel()
        abandonAudioFocus()
        mediaSession?.apply {
            isActive = false
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.englishtutor.session.START"
        const val ACTION_STOP = "com.englishtutor.session.STOP"
        const val EXTRA_LESSON_ID = "lesson_id"
        private const val CHANNEL_ID = "lesson_session"
        private const val NOTIFICATION_ID = 42
    }
}
