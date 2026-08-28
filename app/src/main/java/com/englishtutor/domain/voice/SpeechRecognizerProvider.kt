package com.englishtutor.domain.voice

interface SpeechRecognizerProvider {
    suspend fun recognize(languageCode: String): Result<String>
    fun isAvailable(): Boolean
}
