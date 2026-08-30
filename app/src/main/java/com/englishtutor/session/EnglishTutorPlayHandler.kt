package com.englishtutor.session

import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes headset media buttons to lesson flow (AndroidChat HeadsetPlayHandler pattern).
 */
@Singleton
class EnglishTutorPlayHandler @Inject constructor(
    private val lessonSessionController: LessonSessionController,
    private val logger: AppLogger,
) {
    fun handleBtPlay(source: String = "native") {
        lessonSessionController.handleBtPlay(source)
    }

    fun onMediaButton(buttonLabel: String, source: String = "native") {
        val label = HeadsetButtonNames.normalize(buttonLabel)
        when {
            HeadsetButtonNames.isBtPlayLabel(label) -> handleBtPlay(source)
            label == "MEDIA_NEXT" -> {
                logger.i(TAG, "BT Next ($source)")
                lessonSessionController.onNext()
            }
            label == "MEDIA_PREVIOUS" -> {
                logger.i(TAG, "BT Previous ($source)")
                lessonSessionController.onPrevious()
            }
            label == "MEDIA_STOP" -> {
                logger.i(TAG, "BT Stop ($source) → end lesson session")
                lessonSessionController.stop()
            }
            else -> logger.d(TAG, "Ignored headset button $label ($source)")
        }
    }

    companion object {
        private const val TAG = "Headset"
    }
}
