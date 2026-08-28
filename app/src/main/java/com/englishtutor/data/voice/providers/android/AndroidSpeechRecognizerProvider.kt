package com.englishtutor.data.voice.providers.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class AndroidSpeechRecognizerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : SpeechRecognizerProvider {

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override suspend fun recognize(languageCode: String): Result<String> = withContext(Dispatchers.Main) {
        if (!isAvailable()) {
            logger.e(TAG, "STT unavailable")
            return@withContext Result.failure(IllegalStateException("Speech recognition is not available"))
        }

        logger.i(TAG, "STT listen lang=$languageCode offlinePrefer=true")
        suspendCancellableCoroutine { continuation ->
            val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    logger.d(TAG, "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    logger.d(TAG, "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    logger.d(TAG, "onEndOfSpeech")
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    speechRecognizer.destroy()
                    if (continuation.isActive) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        logger.i(TAG, "STT result=\"$text\"")
                        continuation.resume(Result.success(text))
                    }
                }

                override fun onError(error: Int) {
                    speechRecognizer.destroy()
                    if (continuation.isActive) {
                        logger.e(TAG, "STT error code=$error")
                        continuation.resume(Result.failure(Exception("Speech recognition error: $error")))
                    }
                }
            }

            continuation.invokeOnCancellation {
                logger.w(TAG, "STT cancelled")
                speechRecognizer.destroy()
            }
            speechRecognizer.setRecognitionListener(listener)
            speechRecognizer.startListening(intent)
        }
    }

    companion object {
        private const val TAG = "AndroidSTT"
    }
}
