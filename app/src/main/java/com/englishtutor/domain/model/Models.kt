package com.englishtutor.domain.model

data class Lesson(
    val id: String,
    val title: String,
    val level: String,
    val orderIndex: Int,
    val content: String,
    val practicePhrases: List<String>,
    val languageCode: String,
) {
    val practicePhrase: String get() = practicePhrases.firstOrNull().orEmpty()
}

data class LessonProgress(
    val lessonId: String,
    val completedAt: Long,
    val pronunciationScore: Float?,
)

data class TestResult(
    val id: Long = 0,
    val testType: String,
    val score: Int,
    val level: String,
    val completedAt: Long,
    val details: String?,
)

data class UserProfile(
    val placementLevel: String?,
    val placementCompleted: Boolean,
)

sealed class PlacementQuestion {
    abstract val id: String
    abstract val level: String
    abstract val question: String

    data class MultipleChoice(
        override val id: String,
        override val level: String,
        override val question: String,
        val options: List<String>,
        val correctIndex: Int,
    ) : PlacementQuestion()

    data class Pronunciation(
        override val id: String,
        override val level: String,
        override val question: String,
        val expectedText: String,
        val languageCode: String = "en-US",
    ) : PlacementQuestion()
}

data class ProgressSummary(
    val totalLessons: Int,
    val completedLessons: Int,
    val completionPercent: Int,
    val testResults: List<TestResult>,
    val completedLessonIds: List<String>,
)

enum class LessonPhase {
    IDLE,
    SPEAKING_PHRASE,
    READY_TO_RECORD,
    SPEAKING_FEEDBACK,
    RECORDING,
    COMPLETED,
}

data class LessonSessionState(
    val lessonId: String? = null,
    val lessonTitle: String = "",
    val lessonContent: String = "",
    val languageCode: String = "en-US",
    val feedbackLanguageCode: String = "ru-RU",
    val phrases: List<String> = emptyList(),
    val phraseIndex: Int = 0,
    val phase: LessonPhase = LessonPhase.IDLE,
    val phraseSpoken: Boolean = false,
    val lastRecognizedText: String? = null,
    val pronunciationScore: Float? = null,
    val statusMessage: String? = null,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false,
    val audioFocusHeld: Boolean = false,
) {
    val currentPhrase: String get() = phrases.getOrNull(phraseIndex).orEmpty()
    val phraseProgressLabel: String
        get() = if (phrases.isEmpty()) "0/0" else "${phraseIndex + 1}/${phrases.size}"
}
