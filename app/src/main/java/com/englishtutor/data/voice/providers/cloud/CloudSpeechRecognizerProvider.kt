package com.englishtutor.data.voice.providers.cloud

import com.englishtutor.domain.voice.SpeechRecognizerProvider

/**
 * TODO: Cloud speech recognition provider (Google Cloud Speech, Whisper API, Azure Speech, etc.)
 *
 * Not implemented in MVP — the app works fully offline.
 * When added, wire through [com.englishtutor.data.voice.VoiceProviderFactory]
 * with fallback to local Android provider when offline or API key is missing.
 */
class CloudSpeechRecognizerProvider : SpeechRecognizerProvider {
    override suspend fun recognize(languageCode: String): Result<String> {
        return Result.failure(UnsupportedOperationException("Cloud STT is not implemented yet"))
    }

    override fun isAvailable(): Boolean = false
}
