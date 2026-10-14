package com.englishtutor.data.tutor

import com.englishtutor.data.topics.TopicFileDto
import kotlinx.serialization.json.Json

object TopicJsonExtractor {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val fenceRegex = Regex(
        pattern = "```(?:json)?\\s*\\r?\\n([\\s\\S]*?)```",
        option = RegexOption.IGNORE_CASE,
    )

    fun extract(reply: String): TopicFileDto? {
        val candidates = mutableListOf<String>()
        fenceRegex.findAll(reply).forEach { candidates += it.groupValues[1].trim() }
        // Whole reply might be raw JSON.
        candidates += reply.trim()
        // First { … last } slice.
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start >= 0 && end > start) {
            candidates += reply.substring(start, end + 1)
        }
        for (raw in candidates.distinct()) {
            val dto = runCatching { json.decodeFromString<TopicFileDto>(raw) }.getOrNull() ?: continue
            if (dto.id.isBlank() || dto.titleRu.isBlank()) continue
            if (dto.words.isEmpty() && dto.phrases.isEmpty() && dto.sentences.isEmpty()) continue
            return dto.copy(
                id = dto.id.trim().lowercase().replace(' ', '_'),
                titleRu = dto.titleRu.trim(),
            )
        }
        return null
    }
}
