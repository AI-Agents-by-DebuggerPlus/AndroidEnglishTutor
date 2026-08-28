package com.englishtutor.data.voice

enum class VoiceProviderType {
    LOCAL,
    CLOUD,
}

/**
 * Selects which voice provider implementation to use.
 * Currently always returns LOCAL; structured for future cloud fallback chain.
 */
class VoiceProviderConfig {
    fun speechRecognizerType(): VoiceProviderType = VoiceProviderType.LOCAL

    fun textToSpeechType(): VoiceProviderType = VoiceProviderType.LOCAL
}
