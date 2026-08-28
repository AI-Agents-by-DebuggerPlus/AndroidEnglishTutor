package com.englishtutor.domain.repository

import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.model.LessonProgress
import com.englishtutor.domain.model.PlacementQuestion
import com.englishtutor.domain.model.ProgressSummary
import com.englishtutor.domain.model.TestResult
import com.englishtutor.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface LessonRepository {
    fun observeLessonsFromLevel(level: String): Flow<List<Lesson>>
    suspend fun getLesson(lessonId: String): Lesson?
    suspend fun getLessonsForLevel(level: String): List<Lesson>
    suspend fun ensureContentLoaded()
}

interface ProgressRepository {
    fun observeUserProfile(): Flow<UserProfile>
    suspend fun getUserProfile(): UserProfile
    suspend fun savePlacementResult(level: String, score: Int, details: String)
    suspend fun markLessonCompleted(lessonId: String, pronunciationScore: Float?)
    suspend fun getProgressSummary(): ProgressSummary
    fun observeProgressSummary(): Flow<ProgressSummary>
    suspend fun getPlacementQuestions(): List<PlacementQuestion>
    suspend fun isLessonCompleted(lessonId: String): Boolean
}

interface TestResultRepository {
    suspend fun saveTestResult(result: TestResult)
    fun observeTestResults(): Flow<List<TestResult>>
}
