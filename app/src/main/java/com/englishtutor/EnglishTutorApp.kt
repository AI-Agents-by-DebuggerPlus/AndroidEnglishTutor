package com.englishtutor

import android.app.Application
import com.englishtutor.util.AppLogger
import com.englishtutor.util.AppVersion
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EnglishTutorApp : Application() {

    @Inject lateinit var logger: AppLogger

    override fun onCreate() {
        super.onCreate()
        logger.i("App", "Started · ${AppVersion.label}")
    }
}
