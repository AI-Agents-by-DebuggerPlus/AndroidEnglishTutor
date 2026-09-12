package com.englishtutor.data.voice.providers.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.englishtutor.bluetooth.BluetoothScoHelper
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Singleton
class AndroidSpeechRecognizerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothScoHelper: BluetoothScoHelper,
    private val logger: AppLogger,
) : SpeechRecognizerProvider {

    @Volatile
    private var activeRecognizer: SpeechRecognizer? = null

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun cancel() {
        val wasListening = activeRecognizer != null
        activeRecognizer?.cancel()
        activeRecognizer?.destroy()
        activeRecognizer = null
        bluetoothScoHelper.disable()
        if (wasListening) {
            logger.i(TAG, "STT cancel (was listening)")
        }
    }

    override suspend fun recognize(languageCode: String): Result<String> = withContext(Dispatchers.Main) {
        if (!isAvailable()) {
            logger.e(TAG, "STT unavailable")
            return@withContext Result.failure(IllegalStateException("Speech recognition is not available"))
        }

        val scoReady = bluetoothScoHelper.enableAndWait()
        if (!scoReady) {
            logger.w(TAG, "SCO not ready — STT may use phone mic/speaker")
        }
        // Let routing settle so the system mic cue goes to the headset.
        delay(250)
        logger.i(TAG, "STT listen lang=$languageCode offlinePrefer=true scoReady=$scoReady")
        try {
            suspendCancellableCoroutine { continuation ->
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                activeRecognizer = speechRecognizer
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                val listener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit

                    override fun onResults(results: Bundle?) {
                        speechRecognizer.destroy()
                        activeRecognizer = null
                        // Do not disable SCO here — await media route after recognize() returns
                        // so feedback TTS can play on A2DP headset, not phone speaker.
                        if (continuation.isActive) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull().orEmpty()
                            logger.i(TAG, "STT result=\"$text\"")
                            continuation.resume(Result.success(text))
                        }
                    }

                    override fun onError(error: Int) {
                        speechRecognizer.destroy()
                        activeRecognizer = null
                        if (continuation.isActive) {
                            logger.e(TAG, "STT error code=$error")
                            continuation.resume(Result.failure(Exception("Speech recognition error: $error")))
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    speechRecognizer.cancel()
                    speechRecognizer.destroy()
                    activeRecognizer = null
                    bluetoothScoHelper.disable()
                }
                speechRecognizer.setRecognitionListener(listener)
                speechRecognizer.startListening(intent)
            }
        } finally {
            val mediaReady = bluetoothScoHelper.disableAndAwaitMedia()
            logger.i(TAG, "STT finished · mediaRouteReady=$mediaReady")
        }
    }

    companion object {
        private const val TAG = "AndroidSTT"
    }
}
