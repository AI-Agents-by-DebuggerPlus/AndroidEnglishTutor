package com.englishtutor.domain.voice

interface TextToSpeechProvider {
    suspend fun speak(text: String, languageCode: String)
    fun isAvailable(): Boolean
}
