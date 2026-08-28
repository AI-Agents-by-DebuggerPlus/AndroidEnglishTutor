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
import com.englishtutor.ui.screens.voicetest.VoiceTestScreen

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

        composable(NavRoutes.LOGS) {
            LogsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
