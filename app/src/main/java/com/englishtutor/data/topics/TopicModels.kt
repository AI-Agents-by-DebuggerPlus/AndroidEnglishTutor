package com.englishtutor.data.topics

import kotlinx.serialization.Serializable

enum class StudyStage {
    Words,
    Phrases,
    Sentences,
    ;

    val labelRu: String
        get() = when (this) {
            Words -> "слова"
            Phrases -> "словосочетания"
            Sentences -> "предложения"
        }

    companion object {
        fun fromStored(value: String?): StudyStage =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Words
    }
}

@Serializable
data class TopicFileDto(
    val id: String,
    val titleRu: String,
    val words: List<TopicEntryDto> = emptyList(),
    val phrases: List<TopicEntryDto> = emptyList(),
    val sentences: List<TopicEntryDto> = emptyList(),
)

@Serializable
data class TopicEntryDto(
    val en: String,
    val ru: String,
    val uses: List<String> = emptyList(),
)

data class TopicInfo(
    val id: String,
    val titleRu: String,
    val wordCount: Int,
)

data class StudyCard(
    val en: String,
    val ru: String,
    val stage: StudyStage,
    /** Stable key for progress: topicId|stage|ru */
    val progressKey: String,
    /** English lemma keys this card is built from (for phrases/sentences). */
    val uses: List<String> = emptyList(),
)

data class TopicContent(
    val info: TopicInfo,
    val words: List<StudyCard>,
    val phrases: List<StudyCard>,
    val sentences: List<StudyCard>,
) {
    fun cardsFor(stage: StudyStage): List<StudyCard> = when (stage) {
        StudyStage.Words -> words
        StudyStage.Phrases -> phrases
        StudyStage.Sentences -> sentences
    }
}
