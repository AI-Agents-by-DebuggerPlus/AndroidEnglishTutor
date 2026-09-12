package com.englishtutor.data.voice.providers.android

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.englishtutor.bluetooth.BluetoothScoHelper
import com.englishtutor.data.voice.TtsVoicePreferences
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.domain.voice.TtsVoiceOption
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
    private val voicePreferences: TtsVoicePreferences,
    private val bluetoothScoHelper: BluetoothScoHelper,
    private val logger: AppLogger,
) : TextToSpeechProvider {

    private val initMutex = Mutex()
    private var tts: TextToSpeech? = null
    private var initialized = false

    override fun isAvailable(): Boolean = true

    override fun stopSpeaking() {
        tts?.stop()
    }

    override fun preferredVoiceId(languageCode: String): String? =
        voicePreferences.get(languageCode)

    override fun setPreferredVoiceId(voiceId: String?, languageCode: String) {
        voicePreferences.set(languageCode, voiceId)
        tts?.let { applyPreferredVoice(it, preferredLocale = localeFor(languageCode)) }
        logger.i(TAG, "Preferred TTS voice lang=$languageCode id=${voiceId ?: "default"}")
    }

    override fun clearPreferredVoice(languageCode: String?) {
        voicePreferences.clear(languageCode)
        logger.i(TAG, "Cleared preferred TTS voice lang=${languageCode ?: "all"}")
    }

    override suspend fun listVoices(languageFilter: String?): List<TtsVoiceOption> {
        val engine = ensureInitialized()
        return withContext(Dispatchers.Main) {
            val filter = languageFilter?.lowercase()?.take(2)
            engine.voices.orEmpty()
                .filter { voice ->
                    filter.isNullOrBlank() ||
                        voice.locale.toLanguageTag().lowercase().startsWith(filter) ||
                        voice.locale.language.lowercase() == filter
                }
                .sortedWith(
                    compareByDescending<Voice> { it.quality }
                        .thenBy { it.locale.toLanguageTag() }
                        .thenBy { it.name },
                )
                .map { voice ->
                    TtsVoiceOption(
                        id = voice.name,
                        displayName = friendlyVoiceLabel(voice),
                        languageTag = voice.locale.toLanguageTag(),
                        quality = voice.quality,
                        isNetwork = voice.isNetworkConnectionRequired,
                    )
                }
                .let { makeDisplayNamesUnique(it) }
        }
    }

    private fun friendlyVoiceLabel(voice: Voice): String {
        val tag = voice.locale.toLanguageTag()
        val kind = if (voice.isNetworkConnectionRequired) "сеть" else "офлайн"
        val qualityLabel = when {
            voice.quality >= Voice.QUALITY_VERY_HIGH -> "очень высокий"
            voice.quality >= Voice.QUALITY_HIGH -> "высокий"
            voice.quality >= Voice.QUALITY_NORMAL -> "обычный"
            else -> "базовый"
        }
        val distinct = voice.name
            .replace(tag, "", ignoreCase = true)
            .replace(voice.locale.language, "", ignoreCase = true)
            .trim('-', '_', ' ', '.')
            .ifBlank { voice.name.takeLast(10) }
        return "$tag · $kind · $qualityLabel · $distinct"
    }

    private fun makeDisplayNamesUnique(voices: List<TtsVoiceOption>): List<TtsVoiceOption> {
        val counts = mutableMapOf<String, Int>()
        return voices.map { voice ->
            val base = voice.displayName
            val seen = (counts[base] ?: 0) + 1
            counts[base] = seen
            if (seen == 1) {
                voice
            } else {
                voice.copy(displayName = "$base (#$seen)")
            }
        }
    }

    override suspend fun speak(text: String, languageCode: String) {
        if (!bluetoothScoHelper.isReadyForMediaTts()) {
            logger.i(
                TAG,
                "TTS blocked until A2DP · ${bluetoothScoHelper.snapshot().compact()}",
            )
            val ready = bluetoothScoHelper.awaitA2dpForMediaTts()
            if (!ready) {
                logger.w(
                    TAG,
                    "TTS A2DP not confirmed · ${bluetoothScoHelper.snapshot().compact()} — may use phone speaker",
                )
            }
        }
        val route = bluetoothScoHelper.snapshot()
        val engine = ensureInitialized()
        withContext(Dispatchers.Main) {
            val locale = Locale.forLanguageTag(languageCode.replace('_', '-'))
            engine.language = locale
            applyPreferredVoice(engine, preferredLocale = locale)
            logger.i(
                TAG,
                "TTS speak lang=$languageCode voice=${engine.voice?.name ?: "default"} " +
                    "route=[${route.compact()}] ready=${route.readyForMediaTts} text=\"$text\"",
            )
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    engine.stop()
                }
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
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

                val utteranceId = "english_tutor_tts_${System.currentTimeMillis()}"
                val params = Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                if (result == TextToSpeech.ERROR && continuation.isActive) {
                    continuation.resumeWithException(Exception("Text-to-speech speak() failed"))
                }
            }
        }
    }

    private fun applyPreferredVoice(engine: TextToSpeech, preferredLocale: Locale? = null) {
        val voices = engine.voices ?: return
        val lang = preferredLocale?.language?.takeIf { it.isNotBlank() }
        val preferredId = lang?.let { voicePreferences.get(it) }
        val match = when {
            !preferredId.isNullOrBlank() -> {
                voices.firstOrNull { voice ->
                    voice.name == preferredId &&
                        (lang == null || voice.locale.language.equals(lang, ignoreCase = true))
                } ?: voices.firstOrNull { it.name == preferredId }
            }
            lang != null -> {
                voices
                    .filter { it.locale.language.equals(lang, ignoreCase = true) }
                    .maxByOrNull { it.quality }
            }
            else -> null
        }
        if (match != null) {
            engine.voice = match
            // Keep language in sync with the chosen voice.
            runCatching { engine.language = match.locale }
        } else if (preferredLocale != null) {
            runCatching { engine.language = preferredLocale }
        }
    }

    private fun localeFor(languageCode: String): Locale =
        Locale.forLanguageTag(languageCode.replace('_', '-'))

    private suspend fun ensureInitialized(): TextToSpeech = initMutex.withLock {
        tts?.let { return it }
        suspendCancellableCoroutine { continuation ->
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    initialized = true
                    tts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    applyPreferredVoice(tts!!, preferredLocale = Locale("ru"))
                    logger.i(TAG, "TTS engine ready voices=${tts?.voices?.size ?: 0}")
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
