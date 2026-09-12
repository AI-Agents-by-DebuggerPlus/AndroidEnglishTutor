package com.englishtutor.ui.navigation

object NavRoutes {
    const val SPLASH = "splash"
    const val PLACEMENT = "placement"
    const val HOME = "home"
    const val LESSON = "lesson/{lessonId}"
    const val PROGRESS = "progress"
    const val VOICE_TEST = "voice_test"
    const val VOICE_QUIZ = "voice_quiz"
    const val QUIZ_STATS = "quiz_stats"
    const val VOICE_PICKER = "voice_picker"
    const val WORD_STUDY = "word_study"
    const val LOGS = "logs"

    fun lesson(lessonId: String): String = "lesson/$lessonId"
}
