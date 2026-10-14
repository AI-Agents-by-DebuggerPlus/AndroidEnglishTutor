package com.englishtutor.data.tutor

import com.englishtutor.data.topics.TopicFileDto

data class TutorChatMessage(
    val id: String,
    val role: TutorChatRole,
    val text: String,
    val topicDraft: TopicFileDto? = null,
)

enum class TutorChatRole {
    User,
    Assistant,
    System,
}

/** Link status to Hermes profile english_tutor (Telegram path only). */
enum class TutorLinkState {
    /** TDLib not Ready. */
    TelegramOffline,
    /** Logged in to Telegram; profile not verified yet. */
    TelegramReady,
    /** Test ping in flight. */
    Probing,
    /** Verified reply from english_tutor. */
    EnglishTutorOk,
    /** Ping failed or reply not from english_tutor. */
    EnglishTutorFailed,
}

data class TutorAgentSettings(
    val telegramBotUsername: String = "GoraAcademyFirstBot",
    val telegramApiId: Int = 0,
    val telegramApiHash: String = "",
    val telegramPhone: String = "",
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
)
