package com.englishtutor

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.englishtutor.session.AppSessionManager
import com.englishtutor.ui.navigation.AppNavHost
import com.englishtutor.ui.theme.EnglishTutorTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appSessionManager: AppSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appSessionManager.onExit = {
            finishAffinity()
            Process.killProcess(Process.myPid())
            exitProcess(0)
        }
        enableEdgeToEdge()
        setContent {
            EnglishTutorTheme {
                AppNavHost()
            }
        }
    }
}
