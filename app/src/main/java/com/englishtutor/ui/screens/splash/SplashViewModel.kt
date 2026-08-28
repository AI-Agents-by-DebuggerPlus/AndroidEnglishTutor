package com.englishtutor.ui.screens.splash

import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val lessonRepository: LessonRepository,
) : ViewModel() {

    suspend fun resolveStartDestination(): String {
        lessonRepository.ensureContentLoaded()
        val profile = progressRepository.getUserProfile()
        return if (profile.placementCompleted) NavRoutes.HOME else NavRoutes.PLACEMENT
    }
}
