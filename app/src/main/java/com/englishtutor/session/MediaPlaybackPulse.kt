package com.englishtutor.session

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Short USAGE_MEDIA playback under this app's UID, preferably on BT A2DP.
 * Pulls media routing to the headset after SCO teardown so TTS follows A2DP.
 */
@Singleton
class MediaPlaybackPulse @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    fun pulse() {
        var track: AudioTrack? = null
        try {
            val sampleRate = 44_100
            val durationMs = 180
            val numSamples = sampleRate * durationMs / 1000
            val buffer = ShortArray(numSamples)
            val amplitude = 400
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                buffer[i] = (sin(2.0 * Math.PI * 440.0 * t) * amplitude).toInt().toShort()
            }

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, buffer.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            val a2dp = findA2dpOutput()
            if (a2dp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val ok = track.setPreferredDevice(a2dp)
                logger.i(
                    TAG,
                    "pulse preferred A2DP ok=$ok name=${a2dp.productName}",
                )
            } else {
                logger.w(TAG, "pulse: no A2DP output to prefer")
            }

            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 40L)
            logger.i(TAG, "USAGE_MEDIA pulse ${durationMs}ms")
        } catch (error: Exception) {
            logger.w(TAG, "USAGE_MEDIA pulse failed: ${error.message}")
        } finally {
            runCatching {
                track?.stop()
                track?.release()
            }
        }
    }

    private fun findA2dpOutput(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    companion object {
        private const val TAG = "MediaPulse"
    }
}
