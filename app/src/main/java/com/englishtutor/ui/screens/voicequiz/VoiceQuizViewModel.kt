package com.englishtutor.ui.screens.voicequiz

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishtutor.session.HeadsetButtonNotifier
import com.englishtutor.session.HeadsetMonitorService
import com.englishtutor.session.LessonSessionService
import com.englishtutor.session.VoiceQuizController
import com.englishtutor.session.VoiceQuizState
import com.englishtutor.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class VoiceQuizViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val voiceQuizController: VoiceQuizController,
    private val headsetButtonNotifier: HeadsetButtonNotifier,
    private val logger: AppLogger,
) : ViewModel() {

    val uiState: StateFlow<VoiceQuizState> = voiceQuizController.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = voiceQuizController.state.value,
    )

    fun onScreenVisible() {
        logger.i(TAG, "Voice quiz screen open")
        stopLessonSession()
        headsetButtonNotifier.btPlayTestIsolation = false
        headsetButtonNotifier.isolatedBtPlayHandler = null
        HeadsetMonitorService.reassert(appContext)
        if (!voiceQuizController.isActive) {
            voiceQuizController.activate()
        }
    }

    fun onScreenHidden() {
        // Keep quiz armed for Home / headset Next — do not deactivate.
        logger.i(TAG, "Voice quiz screen closed (quiz stays armed)")
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val mic = grants[android.Manifest.permission.RECORD_AUDIO] == true
        logger.i(TAG, "Mic permission: $mic")
    }

    fun onNext() = voiceQuizController.onNext(source = "ui")

    fun onPlay() = voiceQuizController.onPlay(source = "ui")

    private fun stopLessonSession() {
        runCatching {
            appContext.startService(
                Intent(appContext, LessonSessionService::class.java).apply {
                    action = LessonSessionService.ACTION_STOP
                },
            )
        }
    }

    companion object {
        private const val TAG = "VoiceQuiz"
    }
}
