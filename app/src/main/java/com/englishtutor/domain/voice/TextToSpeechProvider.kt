package com.englishtutor.domain.voice

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val languageTag: String,
    val quality: Int = 0,
    val isNetwork: Boolean = false,
)

interface TextToSpeechProvider {
    suspend fun speak(text: String, languageCode: String)
    fun stopSpeaking() = Unit
    fun isAvailable(): Boolean
    suspend fun listVoices(languageFilter: String? = null): List<TtsVoiceOption> = emptyList()
    fun preferredVoiceId(languageCode: String): String? = null
    fun setPreferredVoiceId(voiceId: String?, languageCode: String) = Unit
    fun clearPreferredVoice(languageCode: String? = null) = Unit
}
