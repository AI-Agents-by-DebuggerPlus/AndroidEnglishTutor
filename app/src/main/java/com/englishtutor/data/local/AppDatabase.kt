package com.englishtutor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.englishtutor.data.local.dao.LessonDao
import com.englishtutor.data.local.dao.LessonProgressDao
import com.englishtutor.data.local.dao.PlacementQuestionDao
import com.englishtutor.data.local.dao.TestResultDao
import com.englishtutor.data.local.dao.UserProfileDao
import com.englishtutor.data.local.entity.LessonEntity
import com.englishtutor.data.local.entity.LessonProgressEntity
import com.englishtutor.data.local.entity.PlacementQuestionEntity
import com.englishtutor.data.local.entity.TestResultEntity
import com.englishtutor.data.local.entity.UserProfileEntity

@Database(
    entities = [
        LessonEntity::class,
        LessonProgressEntity::class,
        TestResultEntity::class,
        UserProfileEntity::class,
        PlacementQuestionEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun testResultDao(): TestResultDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun placementQuestionDao(): PlacementQuestionDao
}
