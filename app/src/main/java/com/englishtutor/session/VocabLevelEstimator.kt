package com.englishtutor.session

import com.englishtutor.data.stats.VoiceQuizSessionStats
import com.englishtutor.data.stats.VoiceQuizStatsFile

/**
 * Local heuristic for CEFR-ish level from vocab progress + recent quiz accuracy.
 * Can later be replaced / assisted by an AI API.
 */
object VocabLevelEstimator {
    fun estimate(
        stats: VoiceQuizStatsFile,
        latestSession: VoiceQuizSessionStats? = null,
    ): String {
        val current = normalize(stats.estimatedLevel.ifBlank { "A1" })
        val guessed = stats.guessedWordSet
        val recent = (listOfNotNull(latestSession) + stats.sessions)
            .filter { it.completed && it.attempts.isNotEmpty() }
            .distinctBy { it.id }
            .take(3)

        val accuracy = if (recent.isEmpty()) {
            null
        } else {
            val correct = recent.sumOf { s -> s.attempts.count { it.correct } }
            val total = recent.sumOf { it.attempts.size }.coerceAtLeast(1)
            correct.toFloat() / total
        }

        val a1Total = VoiceQuizBank.questions.count { it.level == "A1" }.coerceAtLeast(1)
        val a2Total = VoiceQuizBank.questions.count { it.level == "A2" }.coerceAtLeast(1)
        val a1Guessed = VoiceQuizBank.questions.count { it.level == "A1" && it.stimulusRu in guessed }
        val a2Guessed = VoiceQuizBank.questions.count { it.level == "A2" && it.stimulusRu in guessed }
        val a1Ratio = a1Guessed.toFloat() / a1Total
        val a2Ratio = a2Guessed.toFloat() / a2Total

        var next = current
        if (accuracy != null && accuracy < 0.45f) {
            next = demote(current)
        } else {
            when {
                a2Ratio >= 0.55f && (accuracy == null || accuracy >= 0.7f) -> next = "B1"
                a1Ratio >= 0.6f && (accuracy == null || accuracy >= 0.65f) -> {
                    next = if (current == "A1") "A2" else maxOfLevel(current, "A2")
                }
                accuracy != null && accuracy >= 0.85f && current == "A1" && a1Ratio >= 0.35f -> next = "A2"
            }
        }
        return normalize(next)
    }

    private fun demote(level: String): String = when (normalize(level)) {
        "B1" -> "A2"
        "A2" -> "A1"
        else -> "A1"
    }

    private fun maxOfLevel(a: String, b: String): String {
        val ai = VoiceQuizBank.levelIndex(a)
        val bi = VoiceQuizBank.levelIndex(b)
        return if (ai >= bi) normalize(a) else normalize(b)
    }

    fun normalize(level: String): String {
        val up = level.trim().uppercase()
        return if (up in VoiceQuizBank.levels) up else "A1"
    }
}
