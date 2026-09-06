package com.englishtutor.bluetooth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveMediaSessionRow(
    val rank: Int,
    val packageName: String,
    val appLabel: String,
    val playbackState: String,
    val receivesButton: Boolean,
    val isSelf: Boolean,
    val isKnownCompetitor: Boolean,
    val competitorNote: String?,
) {
    fun displayLine(): String {
        val mark = when {
            receivesButton -> "#1"
            isSelf -> "self"
            isKnownCompetitor -> "!"
            else -> "·"
        }
        return "$mark r$rank $appLabel ($packageName) $playbackState"
    }
}

@Singleton
class ActiveSessionsHelper @Inject constructor() {

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val cn = ComponentName(context, NoOpNotificationListener::class.java).flattenToString()
        return flat.split(':').any { it.equals(cn, ignoreCase = true) }
    }

    fun notificationAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun snapshot(context: Context): List<ActiveMediaSessionRow> {
        if (!isNotificationAccessEnabled(context)) {
            return emptyList()
        }
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return emptyList()
        val component = ComponentName(context, NoOpNotificationListener::class.java)
        val controllers = runCatching { msm.getActiveSessions(component) }.getOrElse { emptyList() }
        return mapControllers(context, controllers)
    }

    fun mapControllers(context: Context, controllers: List<MediaController>): List<ActiveMediaSessionRow> {
        val pm = context.packageManager
        val own = context.packageName
        return controllers.mapIndexed { index, controller ->
            val pkg = controller.packageName.orEmpty()
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val state = controller.playbackState
            val stateName = playbackStateName(state?.state ?: PlaybackState.STATE_NONE)
            val competitorNote = knownCompetitors[pkg]
            ActiveMediaSessionRow(
                rank = index + 1,
                packageName = pkg,
                appLabel = label,
                playbackState = stateName,
                receivesButton = index == 0,
                isSelf = pkg == own,
                isKnownCompetitor = competitorNote != null,
                competitorNote = competitorNote,
            )
        }
    }

    private fun playbackStateName(code: Int): String = when (code) {
        PlaybackState.STATE_NONE -> "NONE"
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        else -> "code=$code"
    }

    companion object {
        private val knownCompetitors = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.youtube.music" to "YT Music",
            "com.spotify.music" to "Spotify",
            "com.music.player.mp3player.white" to "MP3 player",
            "net.dinglisch.android.taskerm" to "Tasker",
            "com.taskertowpf.androidchat" to "AndroidChat",
            "com.taskertowpf.androidchatcopyv1" to "AndroidChatCopyV1",
            "com.taskertowpf.androidchatbttest95" to "BtTest95",
            "com.taskertowpf.androidchatbttest93" to "BtTest93",
            "com.taskertowpf.androidchatbttest98" to "BtTest98",
            "com.taskertowpf.androidchatbttestv1" to "BtTestV1",
        )
    }
}
