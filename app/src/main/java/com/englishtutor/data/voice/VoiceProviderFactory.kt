package com.englishtutor.data.voice

import com.englishtutor.data.voice.providers.android.AndroidSpeechRecognizerProvider
import com.englishtutor.data.voice.providers.android.AndroidTtsProvider
import com.englishtutor.data.voice.providers.cloud.CloudSpeechRecognizerProvider
import com.englishtutor.data.voice.providers.cloud.CloudTtsProvider
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceProviderFactory @Inject constructor(
    private val config: VoiceProviderConfig,
    private val androidSpeechRecognizer: AndroidSpeechRecognizerProvider,
    private val androidTts: AndroidTtsProvider,
) {
    fun createSpeechRecognizer(): SpeechRecognizerProvider {
        return when (config.speechRecognizerType()) {
            VoiceProviderType.LOCAL -> androidSpeechRecognizer
            VoiceProviderType.CLOUD -> CloudSpeechRecognizerProvider()
        }
    }

    fun createTextToSpeech(): TextToSpeechProvider {
        return when (config.textToSpeechType()) {
            VoiceProviderType.LOCAL -> androidTts
            VoiceProviderType.CLOUD -> CloudTtsProvider()
        }
    }
}
