package com.englishtutor.session

import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes headset media buttons to word study, voice quiz, or lesson flow.
 */
@Singleton
class EnglishTutorPlayHandler @Inject constructor(
    private val lessonSessionController: LessonSessionController,
    private val voiceQuizController: VoiceQuizController,
    private val wordStudyController: WordStudyController,
    private val logger: AppLogger,
) {
    fun handleBtPlay(source: String = "native") {
        if (wordStudyController.isActive) {
            logger.i(TAG, "BT Play ($source) → word study replay")
            wordStudyController.onPlay(source)
            return
        }
        if (voiceQuizController.isActive) {
            logger.i(TAG, "BT Play ($source) → voice quiz")
            voiceQuizController.onPlay(source)
            return
        }
        if (lessonSessionController.state.value.isActive) {
            lessonSessionController.handleBtPlay(source)
            return
        }
        logger.i(TAG, "BT Play ($source) ignored — press Next to start voice quiz")
    }

    fun onMediaButton(buttonLabel: String, source: String = "native") {
        val label = HeadsetButtonNames.normalize(buttonLabel)
        val studyActive = wordStudyController.isActive
        val quizActive = voiceQuizController.isActive
        val lessonActive = lessonSessionController.state.value.isActive

        when {
            HeadsetButtonNames.isBtPlayLabel(label) ||
                HeadsetButtonNames.isBtPlayGestureLabel(label) -> {
                handleBtPlay(source)
            }
            label == "MEDIA_NEXT" || label == "NEXT" -> {
                when {
                    studyActive -> {
                        logger.i(TAG, "BT Next ($source) → word study")
                        wordStudyController.onNext(source)
                    }
                    quizActive -> {
                        logger.i(TAG, "BT Next ($source) → voice quiz")
                        voiceQuizController.onNext(source)
                    }
                    lessonActive -> {
                        logger.i(TAG, "BT Next ($source) → lesson")
                        lessonSessionController.onNext()
                    }
                    else -> {
                        logger.i(TAG, "BT Next ($source) → start voice quiz")
                        voiceQuizController.activate()
                        voiceQuizController.onNext(source)
                    }
                }
            }
            label == "MEDIA_PREVIOUS" || label == "PREVIOUS" -> {
                if (lessonActive && !quizActive && !studyActive) {
                    logger.i(TAG, "BT Previous ($source)")
                    lessonSessionController.onPrevious()
                } else {
                    logger.d(TAG, "Ignored Previous ($source)")
                }
            }
            label == "MEDIA_STOP" || label == "STOP" -> {
                when {
                    studyActive -> {
                        logger.i(TAG, "BT Stop ($source) → end word study")
                        wordStudyController.deactivate()
                    }
                    quizActive -> {
                        logger.i(TAG, "BT Stop ($source) → end voice quiz")
                        voiceQuizController.deactivate()
                    }
                    lessonActive -> {
                        logger.i(TAG, "BT Stop ($source) → end lesson session")
                        lessonSessionController.stop()
                    }
                    else -> logger.d(TAG, "Ignored Stop ($source)")
                }
            }
            else -> logger.d(TAG, "Ignored headset button $label ($source)")
        }
    }

    companion object {
        private const val TAG = "Headset"
    }
}
