package com.englishtutor.di

import android.content.Context
import androidx.room.Room
import com.englishtutor.data.local.AppDatabase
import com.englishtutor.data.repository.LessonRepositoryImpl
import com.englishtutor.data.repository.ProgressRepositoryImpl
import com.englishtutor.data.repository.TestResultRepositoryImpl
import com.englishtutor.data.voice.VoiceProviderConfig
import com.englishtutor.data.voice.VoiceProviderFactory
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.domain.repository.TestResultRepository
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "english_tutor.db",
        ).fallbackToDestructiveMigration().build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object VoiceModule {

    @Provides
    @Singleton
    fun provideVoiceProviderConfig(): VoiceProviderConfig = VoiceProviderConfig()

    @Provides
    @Singleton
    fun provideSpeechRecognizerProvider(factory: VoiceProviderFactory): SpeechRecognizerProvider {
        return factory.createSpeechRecognizer()
    }

    @Provides
    @Singleton
    fun provideTextToSpeechProvider(factory: VoiceProviderFactory): TextToSpeechProvider {
        return factory.createTextToSpeech()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLessonRepository(impl: LessonRepositoryImpl): LessonRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindTestResultRepository(impl: TestResultRepositoryImpl): TestResultRepository
}
