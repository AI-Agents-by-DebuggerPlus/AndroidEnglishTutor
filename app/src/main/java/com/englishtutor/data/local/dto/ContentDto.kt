package com.englishtutor.data.local.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContentDto(
    val lessons: List<LessonDto>,
    @SerialName("placementTest")
    val placementTest: PlacementTestDto,
)

@Serializable
data class LessonDto(
    val id: String,
    val title: String,
    val level: String,
    val orderIndex: Int,
    val content: String,
    val practicePhrases: List<String> = emptyList(),
    val practicePhrase: String? = null,
    val languageCode: String,
) {
    fun resolvedPhrases(): List<String> {
        return when {
            practicePhrases.isNotEmpty() -> practicePhrases
            !practicePhrase.isNullOrBlank() -> listOf(practicePhrase)
            else -> emptyList()
        }
    }
}

@Serializable
data class PlacementTestDto(
    val questions: List<PlacementQuestionDto>,
)

@Serializable
data class PlacementQuestionDto(
    val id: String,
    val type: String,
    val question: String,
    val level: String,
    val options: List<String>? = null,
    val correctIndex: Int? = null,
    val expectedText: String? = null,
    val languageCode: String? = null,
)
