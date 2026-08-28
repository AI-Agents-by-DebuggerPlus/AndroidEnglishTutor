package com.englishtutor.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: String,
    val title: String,
    val level: String,
    val orderIndex: Int,
    val content: String,
    val practicePhrases: String,
    val languageCode: String,
)

@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val completedAt: Long,
    val pronunciationScore: Float?,
)

@Entity(tableName = "test_results")
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val testType: String,
    val score: Int,
    val level: String,
    val completedAt: Long,
    val details: String?,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val placementLevel: String?,
    val placementCompleted: Boolean,
)

@Entity(tableName = "placement_questions")
data class PlacementQuestionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val question: String,
    val level: String,
    val optionsJson: String?,
    val correctIndex: Int?,
    val expectedText: String?,
    val languageCode: String?,
    val orderIndex: Int,
)
