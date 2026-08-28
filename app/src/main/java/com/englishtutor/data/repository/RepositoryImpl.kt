package com.englishtutor.data.repository

import com.englishtutor.data.local.AppDatabase
import com.englishtutor.data.local.ContentSeeder
import com.englishtutor.data.mapper.toDomain
import com.englishtutor.data.mapper.toEntity
import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.model.PlacementQuestion
import com.englishtutor.domain.model.ProgressSummary
import com.englishtutor.domain.model.TestResult
import com.englishtutor.domain.model.UserProfile
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.domain.repository.TestResultRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@Singleton
class LessonRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val contentSeeder: ContentSeeder,
) : LessonRepository {

    override fun observeLessonsFromLevel(level: String): Flow<List<Lesson>> {
        return database.lessonDao().observeLessonsForLevel(level).map { lessons ->
            lessons.map { it.toDomain() }
        }
    }

    override suspend fun getLesson(lessonId: String): Lesson? {
        ensureContentLoaded()
        return database.lessonDao().getLesson(lessonId)?.toDomain()
    }

    override suspend fun getLessonsForLevel(level: String): List<Lesson> {
        ensureContentLoaded()
        return database.lessonDao().getLessonsForLevel(level).map { it.toDomain() }
    }

    override suspend fun ensureContentLoaded() {
        contentSeeder.seedIfNeeded()
    }
}

@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val contentSeeder: ContentSeeder,
) : ProgressRepository {

    override fun observeUserProfile(): Flow<UserProfile> {
        return database.userProfileDao().observeProfile().map { profile ->
            profile?.toDomain() ?: UserProfile(placementLevel = null, placementCompleted = false)
        }
    }

    override suspend fun getUserProfile(): UserProfile {
        contentSeeder.seedIfNeeded()
        return database.userProfileDao().getProfile()?.toDomain()
            ?: UserProfile(placementLevel = null, placementCompleted = false)
    }

    override suspend fun savePlacementResult(level: String, score: Int, details: String) {
        contentSeeder.seedIfNeeded()
        database.userProfileDao().upsert(
            com.englishtutor.data.local.entity.UserProfileEntity(
                placementLevel = level,
                placementCompleted = true,
            ),
        )
        database.testResultDao().insert(
            TestResult(
                testType = "placement",
                score = score,
                level = level,
                completedAt = System.currentTimeMillis(),
                details = details,
            ).toEntity(),
        )
    }

    override suspend fun markLessonCompleted(lessonId: String, pronunciationScore: Float?) {
        database.lessonProgressDao().insert(
            com.englishtutor.data.local.entity.LessonProgressEntity(
                lessonId = lessonId,
                completedAt = System.currentTimeMillis(),
                pronunciationScore = pronunciationScore,
            ),
        )
    }

    override suspend fun getProgressSummary(): ProgressSummary {
        contentSeeder.seedIfNeeded()
        val profile = getUserProfile()
        val level = profile.placementLevel ?: "A1"
        val lessons = database.lessonDao().getLessonsForLevel(level)
        val progress = database.lessonProgressDao().getAll()
        val testResults = database.testResultDao().getAll()
        val lessonIds = lessons.map { it.id }.toSet()
        val completedInProgram = progress.count { it.lessonId in lessonIds }
        return ProgressSummary(
            totalLessons = lessons.size,
            completedLessons = completedInProgram,
            completionPercent = if (lessons.isEmpty()) 0 else (completedInProgram * 100 / lessons.size),
            testResults = testResults.map { it.toDomain() },
            completedLessonIds = progress.map { it.lessonId },
        )
    }

    override fun observeProgressSummary(): Flow<ProgressSummary> {
        return database.userProfileDao().observeProfile().flatMapLatest { profile ->
            val level = profile?.placementLevel ?: "A1"
            combine(
                database.lessonDao().observeLessonsForLevel(level),
                database.lessonProgressDao().observeAll(),
                database.testResultDao().observeAll(),
            ) { lessons, progress, testResults ->
                val lessonIds = lessons.map { it.id }.toSet()
                val completedInProgram = progress.count { it.lessonId in lessonIds }
                ProgressSummary(
                    totalLessons = lessons.size,
                    completedLessons = completedInProgram,
                    completionPercent = if (lessons.isEmpty()) {
                        0
                    } else {
                        completedInProgram * 100 / lessons.size
                    },
                    testResults = testResults.map { it.toDomain() },
                    completedLessonIds = progress.map { it.lessonId },
                )
            }
        }
    }

    override suspend fun getPlacementQuestions(): List<PlacementQuestion> {
        contentSeeder.seedIfNeeded()
        return database.placementQuestionDao().getAll().map { it.toDomain() }
    }

    override suspend fun isLessonCompleted(lessonId: String): Boolean {
        return database.lessonProgressDao().getProgress(lessonId) != null
    }
}

@Singleton
class TestResultRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
) : TestResultRepository {

    override suspend fun saveTestResult(result: TestResult) {
        database.testResultDao().insert(result.toEntity())
    }

    override fun observeTestResults(): Flow<List<TestResult>> {
        return database.testResultDao().observeAll().map { results ->
            results.map { it.toDomain() }
        }
    }
}
