package com.englishtutor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.englishtutor.ui.screens.home.HomeScreen
import com.englishtutor.ui.screens.lesson.LessonScreen
import com.englishtutor.ui.screens.logs.LogsScreen
import com.englishtutor.ui.screens.placement.PlacementTestScreen
import com.englishtutor.ui.screens.progress.ProgressScreen
import com.englishtutor.ui.screens.splash.SplashScreen
import com.englishtutor.ui.screens.quizstats.QuizStatsScreen
import com.englishtutor.ui.screens.voicepicker.VoicePickerScreen
import com.englishtutor.ui.screens.voicetest.VoiceTestScreen
import com.englishtutor.ui.screens.voicequiz.VoiceQuizScreen
import com.englishtutor.ui.screens.studystats.StudyStatsScreen
import com.englishtutor.ui.screens.wordstudy.WordStudyScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH,
    ) {
        composable(NavRoutes.SPLASH) {
            SplashScreen(
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(NavRoutes.PLACEMENT) {
            PlacementTestScreen(
                onCompleted = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.PLACEMENT) { inclusive = true }
                    }
                },
                onOpenVoiceTest = {
                    navController.navigate(NavRoutes.VOICE_TEST)
                },
                onOpenLogs = {
                    navController.navigate(NavRoutes.LOGS)
                },
            )
        }

        composable(NavRoutes.HOME) {
            HomeScreen(
                onOpenLesson = { lessonId ->
                    navController.navigate(NavRoutes.lesson(lessonId))
                },
                onOpenProgress = {
                    navController.navigate(NavRoutes.PROGRESS)
                },
                onOpenVoiceTest = {
                    navController.navigate(NavRoutes.VOICE_TEST)
                },
                onOpenVoiceQuiz = {
                    navController.navigate(NavRoutes.VOICE_QUIZ)
                },
                onOpenQuizStats = {
                    navController.navigate(NavRoutes.QUIZ_STATS)
                },
                onOpenVoicePicker = {
                    navController.navigate(NavRoutes.VOICE_PICKER)
                },
                onOpenWordStudy = {
                    navController.navigate(NavRoutes.WORD_STUDY)
                },
                onOpenLogs = {
                    navController.navigate(NavRoutes.LOGS)
                },
            )
        }

        composable(
            route = NavRoutes.LESSON,
            arguments = listOf(navArgument("lessonId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId").orEmpty()
            LessonScreen(
                lessonId = lessonId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.PROGRESS) {
            ProgressScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.VOICE_TEST) {
            VoiceTestScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(NavRoutes.LOGS) },
            )
        }

        composable(NavRoutes.VOICE_QUIZ) {
            VoiceQuizScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.QUIZ_STATS) {
            QuizStatsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.VOICE_PICKER) {
            VoicePickerScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.WORD_STUDY) {
            WordStudyScreen(
                onBack = { navController.popBackStack() },
                onOpenVoicePicker = { navController.navigate(NavRoutes.VOICE_PICKER) },
                onOpenStudyStats = { navController.navigate(NavRoutes.STUDY_STATS) },
            )
        }

        composable(NavRoutes.STUDY_STATS) {
            StudyStatsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavRoutes.LOGS) {
            LogsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
