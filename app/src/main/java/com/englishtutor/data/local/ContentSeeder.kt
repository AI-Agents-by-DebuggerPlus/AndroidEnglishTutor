package com.englishtutor.data.local

import android.content.Context
import com.englishtutor.data.local.dto.ContentDto
import com.englishtutor.data.local.entity.LessonEntity
import com.englishtutor.data.local.entity.PlacementQuestionEntity
import com.englishtutor.data.local.entity.UserProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@Singleton
class ContentSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded() = mutex.withLock {
        if (database.lessonDao().countLessons() > 0) return

        val content = context.assets.open("content.json").use { input ->
            json.decodeFromString<ContentDto>(input.reader().readText())
        }

        database.lessonDao().insertLessons(
            content.lessons.map { lesson ->
                LessonEntity(
                    id = lesson.id,
                    title = lesson.title,
                    level = lesson.level,
                    orderIndex = lesson.orderIndex,
                    content = lesson.content,
                    practicePhrases = lesson.resolvedPhrases()
                        .joinToString(com.englishtutor.data.mapper.PHRASES_DELIMITER),
                    languageCode = lesson.languageCode,
                )
            },
        )

        database.placementQuestionDao().insertAll(
            content.placementTest.questions.mapIndexed { index, question ->
                PlacementQuestionEntity(
                    id = question.id,
                    type = question.type,
                    question = question.question,
                    level = question.level,
                    optionsJson = question.options?.joinToString("\u001E"),
                    correctIndex = question.correctIndex,
                    expectedText = question.expectedText,
                    languageCode = question.languageCode,
                    orderIndex = index,
                )
            },
        )

        database.userProfileDao().upsert(
            UserProfileEntity(
                placementLevel = null,
                placementCompleted = false,
            ),
        )
    }
}
