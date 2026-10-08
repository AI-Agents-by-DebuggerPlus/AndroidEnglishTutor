package com.englishtutor.data.voice

import android.content.Context
import com.englishtutor.data.topics.StudyStage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers current study topic, stage, card position and studied keys across restarts.
 */
@Singleton
class StudySessionPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun topicId(defaultId: String): String =
        prefs.getString(KEY_TOPIC_ID, null)?.takeIf { it.isNotBlank() } ?: defaultId

    fun stage(): StudyStage = StudyStage.fromStored(prefs.getString(KEY_STAGE, null))

    fun cardIndex(): Int = prefs.getInt(KEY_CARD_INDEX, 0).coerceAtLeast(0)

    fun batchWordEns(): List<String> =
        prefs.getString(KEY_BATCH_ENS, "")
            ?.split('|')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun studiedKeys(): Set<String> =
        prefs.getStringSet(KEY_STUDIED, emptySet())?.toSet().orEmpty()

    fun savePosition(
        topicId: String,
        stage: StudyStage,
        cardIndex: Int,
        batchWordEns: List<String>,
    ) {
        prefs.edit()
            .putString(KEY_TOPIC_ID, topicId)
            .putString(KEY_STAGE, stage.name)
            .putInt(KEY_CARD_INDEX, cardIndex.coerceAtLeast(0))
            .putString(KEY_BATCH_ENS, batchWordEns.joinToString("|"))
            .apply()
    }

    fun markStudied(progressKey: String) {
        if (progressKey.isBlank()) return
        val next = studiedKeys().toMutableSet().apply { add(progressKey) }
        prefs.edit().putStringSet(KEY_STUDIED, next).apply()
    }

    fun isStudied(progressKey: String): Boolean = progressKey in studiedKeys()

    fun studiedCount(): Int = studiedKeys().size

    fun studiedWordsCount(): Int =
        studiedKeys().count { it.contains("|${StudyStage.Words.name}|") }

    fun studiedPhrasesCount(): Int =
        studiedKeys().count { it.contains("|${StudyStage.Phrases.name}|") }

    fun studiedSentencesCount(): Int =
        studiedKeys().count { it.contains("|${StudyStage.Sentences.name}|") }

    fun viewedTopicIds(): Set<String> =
        prefs.getStringSet(KEY_VIEWED_TOPICS, emptySet())?.toSet().orEmpty()

    fun markTopicViewed(topicId: String) {
        if (topicId.isBlank()) return
        val next = viewedTopicIds().toMutableSet().apply { add(topicId) }
        prefs.edit().putStringSet(KEY_VIEWED_TOPICS, next).apply()
    }

    fun clearViewedTopics() {
        prefs.edit().remove(KEY_VIEWED_TOPICS).apply()
    }

    /**
     * Prefer topics not yet viewed; after all are viewed, reset the cycle and start from the first.
     */
    fun nextTopicId(currentId: String, allIds: List<String>): String? {
        if (allIds.isEmpty()) return null
        val viewed = viewedTopicIds()
        val unviewed = allIds.filter { it !in viewed }
        if (unviewed.isNotEmpty()) {
            val cur = allIds.indexOf(currentId)
            val after = unviewed.filter { allIds.indexOf(it) > cur }
            return after.firstOrNull() ?: unviewed.first()
        }
        clearViewedTopics()
        return allIds.first()
    }

    companion object {
        private const val PREFS = "study_session_prefs"
        private const val KEY_TOPIC_ID = "topic_id"
        private const val KEY_STAGE = "stage"
        private const val KEY_CARD_INDEX = "card_index"
        private const val KEY_BATCH_ENS = "batch_word_ens"
        private const val KEY_STUDIED = "studied_keys"
        private const val KEY_VIEWED_TOPICS = "viewed_topic_ids"
    }
}
