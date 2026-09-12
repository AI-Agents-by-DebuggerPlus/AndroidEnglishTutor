package com.englishtutor.data.stats

import kotlinx.serialization.Serializable

@Serializable
data class VoiceQuizStatsFile(
    val schemaVersion: Int = 3,
    val deviceId: String = "",
    val updatedAtMs: Long = 0L,
    val sessions: List<VoiceQuizSessionStats> = emptyList(),
    /** Unique Russian stimuli answered correctly at least once. */
    val guessedWords: List<String> = emptyList(),
    /** Words that were fully played in study (eligible for tests). */
    val playedWords: List<String> = emptyList(),
    /** Estimated CEFR-ish level from quiz results. */
    val estimatedLevel: String = "A1",
) {
    val totalSessions: Int get() = sessions.size
    val completedSessions: Int get() = sessions.count { it.completed }
    val totalAttempts: Int get() = sessions.sumOf { it.attempts.size }
    val totalCorrectAnswers: Int get() = sessions.sumOf { it.correctAnswers }
    val totalGuessedWords: Int get() = guessedWords.distinct().size
    val totalPlayedWords: Int get() = playedWords.distinct().size
    val guessedWordSet: Set<String> get() = guessedWords.toSet()
    val playedWordSet: Set<String> get() = playedWords.toSet()
}

@Serializable
data class VoiceQuizSessionStats(
    val id: String,
    val startedAtMs: Long,
    val completedAtMs: Long? = null,
    val completed: Boolean = false,
    val questionsTotal: Int = 0,
    val attempts: List<VoiceQuizAttemptStats> = emptyList(),
) {
    val correctAnswers: Int
        get() = attempts.count { it.correct }
    val wrongAttempts: Int
        get() = attempts.count { !it.correct }
}

@Serializable
data class VoiceQuizAttemptStats(
    val stimulusRu: String,
    val spoken: String,
    val correct: Boolean,
    val atMs: Long,
)
