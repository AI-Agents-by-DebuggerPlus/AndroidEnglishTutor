package com.englishtutor.ui.screens.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.bluetooth.BluetoothConnectionMonitor
import com.englishtutor.domain.repository.LessonRepository
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.session.AppSessionManager
import com.englishtutor.ui.navigation.NavRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val progressRepository: ProgressRepository,
    private val lessonRepository: LessonRepository,
    private val appSessionManager: AppSessionManager,
    private val bluetoothConnectionMonitor: BluetoothConnectionMonitor,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    fun refreshBluetooth(context: Context) {
        bluetoothConnectionMonitor.refresh(context)
    }

    fun beginStartup(permissionsReady: Boolean) {
        if (!permissionsReady || _startDestination.value != null) {
            return
        }
        viewModelScope.launch {
            appSessionManager.playStartupGreetingIfNeeded()
            lessonRepository.ensureContentLoaded()
            val profile = progressRepository.getUserProfile()
            val destination = if (profile.placementCompleted) NavRoutes.HOME else NavRoutes.PLACEMENT
            _startDestination.value = destination
        }
    }
}
