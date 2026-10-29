package com.englishtutor.data.topics

import android.content.Context
import android.util.Log
import com.englishtutor.data.tutor.GeneratedTopicStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class TopicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val generatedTopicStore: GeneratedTopicStore,
    private val xShootArchive: XShootArchiveStore,
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

    fun topicsInFolder(folder: String): List<TopicInfo> {
        val key = folder.ifBlank { "Другое" }
        return listTopics().filter { it.folder.ifBlank { "Другое" } == key }
    }

    fun getTopic(id: String): TopicContent? {
        ensureLoaded()
        cache[id]?.let { return it }
        if (id.isNotBlank()) {
            Log.w(TAG, "Topic id='$id' not found; keys=${cache.keys.joinToString()}")
        }
        return cache.values.firstOrNull()
    }

    fun defaultTopicId(): String {
        ensureLoaded()
        return cache.keys.firstOrNull() ?: "general_conversation"
    }

    /** Drop cache and reload assets + generated offline topics. */
    fun reload() {
        cache.clear()
        xShootArchive.reload()
        ensureLoaded()
    }

    fun saveGenerated(dto: TopicFileDto): TopicContent {
        generatedTopicStore.save(dto)
        val content = dto.toContent("Сохранённые")
        cache[dto.id] = content
        return content
    }

    private fun ensureLoaded() {
        if (cache.isNotEmpty()) return
        loadFromAssets()
        loadFromFiles(generatedTopicStore.listFiles())
        Log.i(TAG, "Topics loaded: ${cache.keys.joinToString()}")
    }

    private fun loadFromAssets() {
        val files = mutableListOf<String>()
        collectTopicJson(TOPICS_DIR, files)
        Log.i(TAG, "Topic assets: ${files.joinToString()}")
        files.sortedBy { path ->
            orderIndex(path.substringAfterLast('/').removeSuffix(".json"))
        }.forEach { path ->
            runCatching {
                context.assets.open(path).bufferedReader().use { reader ->
                    val dto = json.decodeFromString<TopicFileDto>(reader.readText())
                    val relative = path.removePrefix("$TOPICS_DIR/")
                    val folder = relative.substringBeforeLast('/', missingDelimiterValue = "")
                    cache[dto.id] = dto.toContent(folder)
                }
            }.onFailure {
                Log.e(TAG, "Failed to load topic $path: ${it.message}", it)
            }
        }
    }

    private fun collectTopicJson(dir: String, out: MutableList<String>) {
        val names = runCatching {
            context.assets.list(dir)?.toList().orEmpty()
        }.onFailure {
            Log.e(TAG, "assets.list($dir) failed: ${it.message}")
        }.getOrDefault(emptyList())
        for (name in names) {
            val path = "$dir/$name"
            if (name.endsWith(".json")) {
                out.add(path)
            } else {
                val children = context.assets.list(path)
                if (!children.isNullOrEmpty()) {
                    collectTopicJson(path, out)
                }
            }
        }
    }

    private fun loadFromFiles(files: List<File>) {
        files.forEach { file ->
            runCatching {
                val dto = json.decodeFromString<TopicFileDto>(file.readText(Charsets.UTF_8))
                cache[dto.id] = dto.toContent("Сохранённые")
                Log.i(TAG, "Loaded generated topic ${dto.id} from ${file.name}")
            }.onFailure {
                Log.e(TAG, "Failed to load generated ${file.name}: ${it.message}", it)
            }
        }
    }

    private fun TopicFileDto.toContent(folder: String): TopicContent {
        val known = xShootArchive.knownEnglish()
        // Archive words are skipped as standalone study cards, but may still appear in phrases/sentences.
        val words = words
            .filter { it.en.trim().lowercase() !in known }
            .map {
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
            info = TopicInfo(id = id, titleRu = titleRu, wordCount = words.size, folder = folder),
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
            "everyday_thousand",
            "bridge_style_a1",
            "bridge_dictation_monologues",
            "bridge_dictation_dialogues",
            "level_a2",
            "level_b1",
            "b1_tech_thousand",
            "celpip_practice",
            "clb5_week1",
            "clb5_week2",
            "clb5_week3",
            "clb5_checklists",
            "food",
            "tools",
            "bottle_depot",
            "ai_engineering",
            "trading",
            "hermes_english_practice",
        )

        fun progressKey(topicId: String, stage: StudyStage, ru: String): String =
            "$topicId|${stage.name}|${ru.trim()}"

        private fun orderIndex(id: String): Int {
            val idx = ORDER.indexOf(id)
            return if (idx >= 0) idx else 1000
        }
    }
}
