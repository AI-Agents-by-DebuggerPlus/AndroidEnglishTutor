package com.englishtutor.data.mapper

import com.englishtutor.data.local.entity.LessonEntity
import com.englishtutor.data.local.entity.LessonProgressEntity
import com.englishtutor.data.local.entity.PlacementQuestionEntity
import com.englishtutor.data.local.entity.TestResultEntity
import com.englishtutor.data.local.entity.UserProfileEntity
import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.model.LessonProgress
import com.englishtutor.domain.model.PlacementQuestion
import com.englishtutor.domain.model.TestResult
import com.englishtutor.domain.model.UserProfile

private const val OPTIONS_DELIMITER = "\u001E"
const val PHRASES_DELIMITER = "\u001E"

fun LessonEntity.toDomain(): Lesson = Lesson(
    id = id,
    title = title,
    level = level,
    orderIndex = orderIndex,
    content = content,
    practicePhrases = practicePhrases.split(PHRASES_DELIMITER).filter { it.isNotBlank() },
    languageCode = languageCode,
)

fun LessonProgressEntity.toDomain(): LessonProgress = LessonProgress(
    lessonId = lessonId,
    completedAt = completedAt,
    pronunciationScore = pronunciationScore,
)

fun TestResultEntity.toDomain(): TestResult = TestResult(
    id = id,
    testType = testType,
    score = score,
    level = level,
    completedAt = completedAt,
    details = details,
)

fun UserProfileEntity.toDomain(): UserProfile = UserProfile(
    placementLevel = placementLevel,
    placementCompleted = placementCompleted,
)

fun PlacementQuestionEntity.toDomain(): PlacementQuestion {
    return when (type) {
        "pronunciation" -> PlacementQuestion.Pronunciation(
            id = id,
            level = level,
            question = question,
            expectedText = expectedText.orEmpty(),
            languageCode = languageCode ?: "en-US",
        )
        else -> PlacementQuestion.MultipleChoice(
            id = id,
            level = level,
            question = question,
            options = optionsJson?.split(OPTIONS_DELIMITER).orEmpty(),
            correctIndex = correctIndex ?: 0,
        )
    }
}

fun TestResult.toEntity(): TestResultEntity = TestResultEntity(
    id = id,
    testType = testType,
    score = score,
    level = level,
    completedAt = completedAt,
    details = details,
)
