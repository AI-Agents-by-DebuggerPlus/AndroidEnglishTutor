package com.englishtutor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.englishtutor.data.local.entity.LessonEntity
import com.englishtutor.data.local.entity.LessonProgressEntity
import com.englishtutor.data.local.entity.PlacementQuestionEntity
import com.englishtutor.data.local.entity.TestResultEntity
import com.englishtutor.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE level = :level ORDER BY orderIndex ASC")
    fun observeLessonsForLevel(level: String): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE level = :level ORDER BY orderIndex ASC")
    suspend fun getLessonsForLevel(level: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLesson(lessonId: String): LessonEntity?

    @Query("SELECT COUNT(*) FROM lessons")
    suspend fun countLessons(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)
}

@Dao
interface LessonProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: LessonProgressEntity)

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :lessonId LIMIT 1")
    suspend fun getProgress(lessonId: String): LessonProgressEntity?

    @Query("SELECT * FROM lesson_progress ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress ORDER BY completedAt DESC")
    suspend fun getAll(): List<LessonProgressEntity>

    @Query("SELECT COUNT(*) FROM lesson_progress")
    suspend fun countCompleted(): Int
}

@Dao
interface TestResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: TestResultEntity): Long

    @Query("SELECT * FROM test_results ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results ORDER BY completedAt DESC")
    suspend fun getAll(): List<TestResultEntity>
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface PlacementQuestionDao {
    @Query("SELECT * FROM placement_questions ORDER BY orderIndex ASC")
    suspend fun getAll(): List<PlacementQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<PlacementQuestionEntity>)

    @Query("SELECT COUNT(*) FROM placement_questions")
    suspend fun count(): Int
}
