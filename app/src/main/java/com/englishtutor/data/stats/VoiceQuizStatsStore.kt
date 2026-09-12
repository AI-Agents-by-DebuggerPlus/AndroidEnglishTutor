package com.englishtutor.data.stats

import android.content.Context
import com.englishtutor.session.VocabLevelEstimator
import com.englishtutor.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists voice-quiz statistics as JSON under app filesDir.
 */
@Singleton
class VoiceQuizStatsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _stats = MutableStateFlow(VoiceQuizStatsFile())
    val stats: StateFlow<VoiceQuizStatsFile> = _stats.asStateFlow()

    val filePath: String get() = statsFile().absolutePath

    init {
        runCatching { _stats.value = readUnlocked() }
            .onFailure { logger.w(TAG, "Stats load failed: ${it.message}") }
    }

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    suspend fun current(): VoiceQuizStatsFile = mutex.withLock {
        _stats.value
    }

    fun guessedWords(): Set<String> = _stats.value.guessedWordSet

    fun playedWords(): Set<String> = _stats.value.playedWordSet

    fun estimatedLevel(): String =
        VocabLevelEstimator.normalize(_stats.value.estimatedLevel.ifBlank { "A1" })

    suspend fun markPlayed(stimulusRu: String): VoiceQuizStatsFile = mutex.withLock {
        if (stimulusRu.isBlank()) return@withLock _stats.value
        val current = readUnlocked()
        if (stimulusRu in current.playedWordSet) return@withLock current
        val merged = current.copy(
            schemaVersion = 3,
            deviceId = current.deviceId.ifBlank { deviceId() },
            updatedAtMs = System.currentTimeMillis(),
            playedWords = (current.playedWords + stimulusRu).distinct(),
        )
        writeUnlocked(merged)
        _stats.value = merged
        logger.i(TAG, "Played word «$stimulusRu» · total=${merged.totalPlayedWords}")
        merged
    }

    suspend fun markGuessed(stimulusRu: String): VoiceQuizStatsFile = mutex.withLock {
        if (stimulusRu.isBlank()) return@withLock _stats.value
        val current = readUnlocked()
        if (stimulusRu in current.guessedWordSet) return@withLock current
        val merged = current.copy(
            schemaVersion = 3,
            deviceId = current.deviceId.ifBlank { deviceId() },
            updatedAtMs = System.currentTimeMillis(),
            guessedWords = (current.guessedWords + stimulusRu).distinct(),
            playedWords = (current.playedWords + stimulusRu).distinct(),
        )
        writeUnlocked(merged)
        _stats.value = merged
        logger.i(TAG, "Guessed word «$stimulusRu» · total=${merged.totalGuessedWords}")
        merged
    }

    suspend fun setEstimatedLevel(level: String): VoiceQuizStatsFile = mutex.withLock {
        val normalized = VocabLevelEstimator.normalize(level)
        val current = readUnlocked()
        if (current.estimatedLevel == normalized) return@withLock current
        val merged = current.copy(
            schemaVersion = 3,
            deviceId = current.deviceId.ifBlank { deviceId() },
            updatedAtMs = System.currentTimeMillis(),
            estimatedLevel = normalized,
        )
        writeUnlocked(merged)
        _stats.value = merged
        logger.i(TAG, "Estimated level → $normalized")
        merged
    }

    suspend fun appendSession(session: VoiceQuizSessionStats): VoiceQuizStatsFile = mutex.withLock {
        val current = readUnlocked()
        val newlyGuessed = session.attempts
            .filter { it.correct }
            .map { it.stimulusRu }
            .filter { it.isNotBlank() }
        val mergedGuessed = (current.guessedWords + newlyGuessed).distinct()
        val withSession = current.copy(
            schemaVersion = 3,
            deviceId = current.deviceId.ifBlank { deviceId() },
            updatedAtMs = System.currentTimeMillis(),
            sessions = (listOf(session) + current.sessions).take(MAX_SESSIONS),
            guessedWords = mergedGuessed,
            playedWords = (current.playedWords + newlyGuessed).distinct(),
        )
        val level = VocabLevelEstimator.estimate(withSession, latestSession = session)
        val merged = withSession.copy(estimatedLevel = level)
        writeUnlocked(merged)
        _stats.value = merged
        logger.i(
            TAG,
            "Stats saved session=${session.id} attempts=${session.attempts.size} " +
                "guessed=${merged.totalGuessedWords} level=$level → ${statsFile().name}",
        )
        merged
    }

    suspend fun replaceAll(file: VoiceQuizStatsFile): VoiceQuizStatsFile = mutex.withLock {
        val applied = file.copy(
            schemaVersion = 3,
            deviceId = file.deviceId.ifBlank { deviceId() },
            updatedAtMs = System.currentTimeMillis(),
            sessions = file.sessions.take(MAX_SESSIONS),
            guessedWords = file.guessedWords.distinct(),
            playedWords = file.playedWords.distinct(),
            estimatedLevel = VocabLevelEstimator.normalize(file.estimatedLevel.ifBlank { "A1" }),
        )
        writeUnlocked(applied)
        _stats.value = applied
        logger.i(TAG, "Stats applied from import · sessions=${applied.sessions.size}")
        applied
    }

    suspend fun clear(): VoiceQuizStatsFile = mutex.withLock {
        val empty = VoiceQuizStatsFile(
            schemaVersion = 3,
            deviceId = deviceId(),
            updatedAtMs = System.currentTimeMillis(),
            guessedWords = emptyList(),
            playedWords = emptyList(),
            estimatedLevel = "A1",
        )
        writeUnlocked(empty)
        _stats.value = empty
        logger.i(TAG, "Stats cleared")
        empty
    }

    suspend fun exportJson(): String = mutex.withLock {
        json.encodeToString(readUnlocked())
    }

    fun decodeJson(raw: String): VoiceQuizStatsFile = json.decodeFromString(raw)

    private fun statsFile(): File = File(context.filesDir, FILE_NAME)

    private fun readUnlocked(): VoiceQuizStatsFile {
        val file = statsFile()
        if (!file.exists() || file.length() == 0L) {
            return VoiceQuizStatsFile(deviceId = deviceId(), updatedAtMs = System.currentTimeMillis())
        }
        return runCatching {
            json.decodeFromString<VoiceQuizStatsFile>(file.readText(Charsets.UTF_8))
                .let { loaded ->
                    val withDevice = if (loaded.deviceId.isBlank()) {
                        loaded.copy(deviceId = deviceId())
                    } else {
                        loaded
                    }
                    migrate(withDevice)
                }
        }.getOrElse {
            logger.w(TAG, "Corrupt stats file, resetting: ${it.message}")
            VoiceQuizStatsFile(deviceId = deviceId(), updatedAtMs = System.currentTimeMillis())
        }
    }

    private fun migrate(file: VoiceQuizStatsFile): VoiceQuizStatsFile {
        var result = file
        if (result.guessedWords.isEmpty()) {
            val fromSessions = result.sessions
                .flatMap { session -> session.attempts.filter { it.correct }.map { it.stimulusRu } }
                .filter { it.isNotBlank() }
                .distinct()
            if (fromSessions.isNotEmpty()) {
                result = result.copy(guessedWords = fromSessions)
            }
        }
        if (result.playedWords.isEmpty() && result.guessedWords.isNotEmpty()) {
            // Previously mastered words were at least shown in tests — treat as played.
            result = result.copy(playedWords = result.guessedWords)
        }
        val level = VocabLevelEstimator.normalize(result.estimatedLevel.ifBlank { "A1" })
        return result.copy(schemaVersion = 3, estimatedLevel = level)
    }

    private fun writeUnlocked(file: VoiceQuizStatsFile) {
        val target = statsFile()
        val tmp = File(target.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.encodeToString(file), Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.writeText(json.encodeToString(file), Charsets.UTF_8)
            tmp.delete()
        }
    }

    suspend fun reload(): VoiceQuizStatsFile = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = readUnlocked()
            _stats.value = loaded
            loaded
        }
    }

    companion object {
        private const val TAG = "QuizStats"
        private const val FILE_NAME = "voice_quiz_stats.json"
        private const val PREFS = "voice_quiz_stats_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val MAX_SESSIONS = 200
        const val SERVER_PREFIX = "[STATS:VoiceQuiz]"
    }
}
