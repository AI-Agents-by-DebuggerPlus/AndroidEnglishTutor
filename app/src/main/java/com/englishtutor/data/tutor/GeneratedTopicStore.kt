package com.englishtutor.data.tutor

import android.content.Context
import com.englishtutor.data.topics.TopicFileDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class GeneratedTopicStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun topicsDir(): File = File(context.filesDir, TOPICS_DIR).also { it.mkdirs() }

    fun save(dto: TopicFileDto): File {
        val id = dto.id.trim().lowercase().replace(' ', '_')
        val file = File(topicsDir(), "$id.json")
        file.writeText(json.encodeToString(dto.copy(id = id)), Charsets.UTF_8)
        return file
    }

    fun listFiles(): List<File> =
        topicsDir().listFiles()?.filter { it.isFile && it.name.endsWith(".json") }.orEmpty()
            .sortedBy { it.name }

    companion object {
        const val TOPICS_DIR = "topics"
    }
}
