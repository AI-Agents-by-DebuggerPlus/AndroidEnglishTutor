package com.englishtutor.data.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseLogSettings(
    val supabaseUrl: String = "",
    val supabaseAnonKey: String = "",
    val senderName: String = "AndroidEnglishTutor",
    val logRecipientName: String = "WpfChat",
    val useAnonymousAuth: Boolean = true,
    val skipDuplicateLogsToSupabase: Boolean = true,
)

@Serializable
data class MessageInsert(
    @SerialName("sender_id")
    val senderId: String,
    @SerialName("sender_name")
    val senderName: String,
    @SerialName("recipient_name")
    val recipientName: String,
    val content: String,
    @SerialName("created_at")
    val createdAt: String,
)

data class LogUploadResult(
    val sent: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
) {
    val summary: String
        get() = "отправлено $sent, ошибок $failed, пропущено $skipped"
}
