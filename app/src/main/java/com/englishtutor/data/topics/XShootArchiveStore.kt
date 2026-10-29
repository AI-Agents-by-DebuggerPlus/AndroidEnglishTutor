package com.englishtutor.data.topics

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * English lemmas guessed in Desktop XSHOOT. Used to skip standalone word cards
 * while still allowing those lemmas inside phrases and sentences.
 */
@Singleton
class XShootArchiveStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cache: Set<String>? = null

    fun knownEnglish(): Set<String> {
        cache?.let { return it }
        val keys = linkedSetOf<String>()
        loadFromAssets(keys)
        loadFromFile(File(context.filesDir, ARCHIVE_FILE), keys)
        cache = keys
        Log.i(TAG, "XShoot archive loaded: ${keys.size} words")
        return keys
    }

    fun reload() {
        cache = null
        knownEnglish()
    }

    /** Import / overwrite archive from JSON text (e.g. after adb push). */
    fun importJson(raw: String) {
        val parsed = runCatching { json.decodeFromString<GuessedArchiveDto>(raw) }.getOrNull()
        val words = parsed?.words.orEmpty()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
        File(context.filesDir, ARCHIVE_FILE).writeText(
            json.encodeToString(GuessedArchiveDto(words = words)),
            Charsets.UTF_8,
        )
        cache = words.toSet()
    }

    private fun loadFromAssets(into: MutableSet<String>) {
        runCatching {
            context.assets.open(ARCHIVE_FILE).bufferedReader().use { reader ->
                val dto = json.decodeFromString<GuessedArchiveDto>(reader.readText())
                dto.words.forEach { into.add(it.trim().lowercase()) }
            }
        }.onFailure {
            Log.d(TAG, "No asset archive yet: ${it.message}")
        }
    }

    private fun loadFromFile(file: File, into: MutableSet<String>) {
        if (!file.isFile) return
        runCatching {
            val dto = json.decodeFromString<GuessedArchiveDto>(file.readText(Charsets.UTF_8))
            dto.words.forEach { into.add(it.trim().lowercase()) }
        }.onFailure {
            Log.w(TAG, "Failed to read ${file.name}: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "XShootArchive"
        const val ARCHIVE_FILE = "xshoot_archive.json"
    }
}

@Serializable
data class GuessedArchiveDto(
    val version: Int = 1,
    val updatedUtc: String = "",
    val words: List<String> = emptyList(),
)
