package com.englishtutor.data.topics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class TopicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val cache = linkedMapOf<String, TopicContent>()

    fun listTopics(): List<TopicInfo> {
        ensureLoaded()
        return cache.values.map { it.info }
    }

    fun getTopic(id: String): TopicContent? {
        ensureLoaded()
        return cache[id] ?: cache.values.firstOrNull()
    }

    fun defaultTopicId(): String {
        ensureLoaded()
        return cache.keys.firstOrNull() ?: "general_conversation"
    }

    private fun ensureLoaded() {
        if (cache.isNotEmpty()) return
        val names = runCatching {
            context.assets.list(TOPICS_DIR)?.toList().orEmpty()
        }.onFailure {
            Log.e(TAG, "assets.list($TOPICS_DIR) failed: ${it.message}")
        }.getOrDefault(emptyList())
        Log.i(TAG, "Topic assets: ${names.joinToString()}")
        names.filter { it.endsWith(".json") }
            .sortedBy { orderIndex(it.removeSuffix(".json")) }
            .forEach { file ->
                runCatching {
                    context.assets.open("$TOPICS_DIR/$file").bufferedReader().use { reader ->
                        val dto = json.decodeFromString<TopicFileDto>(reader.readText())
                        cache[dto.id] = dto.toContent()
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to load topic $file: ${it.message}", it)
                }
            }
        Log.i(TAG, "Topics loaded: ${cache.keys.joinToString()}")
    }

    private fun TopicFileDto.toContent(): TopicContent {
        val words = words.map {
            StudyCard(
                en = it.en.trim(),
                ru = it.ru.trim(),
                stage = StudyStage.Words,
                progressKey = progressKey(id, StudyStage.Words, it.ru),
                uses = listOf(it.en.trim().lowercase()),
            )
        }
        val phrases = phrases.map {
            StudyCard(
                en = it.en.trim(),
                ru = it.ru.trim(),
                stage = StudyStage.Phrases,
                progressKey = progressKey(id, StudyStage.Phrases, it.ru),
                uses = it.uses.map { u -> u.trim().lowercase() },
            )
        }
        val sentences = sentences.map {
            StudyCard(
                en = it.en.trim(),
                ru = it.ru.trim(),
                stage = StudyStage.Sentences,
                progressKey = progressKey(id, StudyStage.Sentences, it.ru),
                uses = it.uses.map { u -> u.trim().lowercase() },
            )
        }
        return TopicContent(
            info = TopicInfo(id = id, titleRu = titleRu, wordCount = words.size),
            words = words,
            phrases = phrases,
            sentences = sentences,
        )
    }

    companion object {
        private const val TAG = "TopicRepo"
        private const val TOPICS_DIR = "topics"

        private val ORDER = listOf(
            "general_conversation",
            "food",
            "tools",
            "bottle_depot",
            "ai_engineering",
            "trading",
        )

        fun progressKey(topicId: String, stage: StudyStage, ru: String): String =
            "$topicId|${stage.name}|${ru.trim()}"

        private fun orderIndex(id: String): Int {
            val idx = ORDER.indexOf(id)
            return if (idx >= 0) idx else 1000
        }
    }
}
