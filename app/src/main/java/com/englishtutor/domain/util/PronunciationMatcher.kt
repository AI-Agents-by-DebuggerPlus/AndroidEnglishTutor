package com.englishtutor.domain.util

object PronunciationMatcher {
    fun similarity(expected: String, spoken: String): Float {
        val normalizedExpected = normalize(expected)
        val normalizedSpoken = normalize(spoken)
        if (normalizedExpected.isEmpty() && normalizedSpoken.isEmpty()) return 1f
        if (normalizedExpected.isEmpty() || normalizedSpoken.isEmpty()) return 0f

        val distance = levenshtein(normalizedExpected, normalizedSpoken)
        val maxLen = maxOf(normalizedExpected.length, normalizedSpoken.length)
        return (1f - distance.toFloat() / maxLen).coerceIn(0f, 1f)
    }

    fun isMatch(expected: String, spoken: String, threshold: Float = 0.7f): Boolean {
        return similarity(expected, spoken) >= threshold
    }

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val costs = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previousDiagonal = costs[0]
            costs[0] = i
            for (j in 1..b.length) {
                val temp = costs[j]
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    previousDiagonal + substitutionCost,
                )
                previousDiagonal = temp
            }
        }
        return costs[b.length]
    }
}
