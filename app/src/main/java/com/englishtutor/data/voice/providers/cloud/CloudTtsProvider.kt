package com.englishtutor.data.voice.providers.cloud

import com.englishtutor.domain.voice.TextToSpeechProvider

/**
 * TODO: Cloud text-to-speech provider (ElevenLabs, Google Cloud TTS, Azure Speech, etc.)
 *
 * Not implemented in MVP — the app uses system offline voices via [AndroidTtsProvider].
 * When added, select provider via [com.englishtutor.data.voice.VoiceProviderConfig].
 */
class CloudTtsProvider : TextToSpeechProvider {
    override suspend fun speak(text: String, languageCode: String) {
        throw UnsupportedOperationException("Cloud TTS is not implemented yet")
    }

    override fun isAvailable(): Boolean = false
}
