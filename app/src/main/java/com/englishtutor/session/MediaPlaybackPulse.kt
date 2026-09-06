package com.englishtutor.session

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Short USAGE_MEDIA playback under this app's UID.
 * Google TTS cue alone attributes audio to com.google.android.tts, so Android
 * may leave Media button session null — BtTest95 wins because its UID plays media.
 * See Docs/Tasks/Cursor/AndEngTutor_BT_Play_Hardware_Fix_Agent_Instruction.md §4 C3.1
 */
@Singleton
class MediaPlaybackPulse @Inject constructor(
    private val logger: AppLogger,
) {
    fun pulse() {
        var track: AudioTrack? = null
        try {
            val sampleRate = 44_100
            val durationMs = 180
            val numSamples = sampleRate * durationMs / 1000
            val buffer = ShortArray(numSamples)
            // Very quiet 440 Hz tone — enough for system to count "real" media playback.
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
            track.write(buffer, 0, buffer.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 40L)
            logger.i(TAG, "USAGE_MEDIA pulse done (${durationMs}ms)")
        } catch (error: Exception) {
            logger.w(TAG, "USAGE_MEDIA pulse failed: ${error.message}")
        } finally {
            runCatching {
                track?.stop()
                track?.release()
            }
        }
    }

    companion object {
        private const val TAG = "MediaPulse"
    }
}
