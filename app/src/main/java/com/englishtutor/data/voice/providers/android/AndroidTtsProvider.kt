package com.englishtutor.data.voice.providers.android

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AndroidTtsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : TextToSpeechProvider {

    private val initMutex = Mutex()
    private var tts: TextToSpeech? = null
    private var initialized = false

    override fun isAvailable(): Boolean = initialized || true

    override fun stopSpeaking() {
        tts?.stop()
    }

    override suspend fun speak(text: String, languageCode: String) {
        val engine = ensureInitialized()
        withContext(Dispatchers.Main) {
            val locale = Locale.forLanguageTag(languageCode.replace('_', '-'))
            engine.language = locale
            logger.i(TAG, "TTS speak lang=$languageCode text=\"$text\"")
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        logger.d(TAG, "TTS done")
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        logger.e(TAG, "TTS error")
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("Text-to-speech failed"))
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        logger.e(TAG, "TTS error code=$errorCode")
                        if (continuation.isActive) {
                            continuation.resumeWithException(Exception("Text-to-speech failed: $errorCode"))
                        }
                    }
                })

                val utteranceId = "english_tutor_tts"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                } else {
                    @Suppress("DEPRECATION")
                    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null)
                }
            }
        }
    }

    private suspend fun ensureInitialized(): TextToSpeech = initMutex.withLock {
        tts?.let { return it }
        suspendCancellableCoroutine { continuation ->
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    initialized = true
                    logger.i(TAG, "TTS engine ready")
                    continuation.resume(tts!!)
                } else if (continuation.isActive) {
                    logger.e(TAG, "TTS init failed status=$status")
                    continuation.resumeWithException(Exception("Text-to-speech initialization failed"))
                }
            }
        }
    }

    companion object {
        private const val TAG = "AndroidTTS"
    }
}
