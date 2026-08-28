package com.englishtutor.session

import com.englishtutor.domain.model.Lesson
import com.englishtutor.domain.model.LessonPhase
import com.englishtutor.domain.model.LessonSessionState
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.domain.util.PronunciationMatcher
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Eyes-free lesson flow driven by headset AVRCP commands.
 * UI only mirrors [state]; primary control is Play/Pause, Next, Previous.
 */
@Singleton
class LessonSessionController @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val speechRecognizer: SpeechRecognizerProvider,
    private val progressRepository: ProgressRepository,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commandMutex = Mutex()
    private var activeJob: Job? = null

    private val _state = MutableStateFlow(LessonSessionState())
    val state: StateFlow<LessonSessionState> = _state.asStateFlow()

    fun start(lesson: Lesson, alreadyCompleted: Boolean) {
        activeJob?.cancel()
        logger.i(TAG, "Session start lesson=${lesson.id} phrases=${lesson.practicePhrases.size}")
        _state.value = LessonSessionState(
            lessonId = lesson.id,
            lessonTitle = lesson.title,
            lessonContent = lesson.content,
            languageCode = lesson.languageCode,
            phrases = lesson.practicePhrases,
            phraseIndex = 0,
            phase = LessonPhase.IDLE,
            phraseSpoken = false,
            isActive = true,
            isCompleted = alreadyCompleted,
            statusMessage = if (alreadyCompleted) {
                "Урок уже пройден. Play — повторить фразу."
            } else {
                "Play — прослушать. Next — пропустить. Previous — сначала."
            },
        )
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        logger.i(TAG, "Session stop")
        _state.update {
            it.copy(
                isActive = false,
                phase = LessonPhase.IDLE,
                statusMessage = "Сессия остановлена",
            )
        }
    }

    fun setAudioFocusHeld(held: Boolean) {
        _state.update { it.copy(audioFocusHeld = held) }
    }

    /** Play/Pause: speak phrase, or start recording if phrase already spoken. */
    fun onPlayPause() {
        logger.d(TAG, "AVRCP play/pause phraseSpoken=${_state.value.phraseSpoken}")
        enqueue {
            val current = _state.value
            if (!current.isActive || current.phrases.isEmpty()) return@enqueue
            if (current.phase == LessonPhase.COMPLETED) {
                speakFeedback("Урок уже завершён")
                return@enqueue
            }
            if (current.phase == LessonPhase.RECORDING ||
                current.phase == LessonPhase.SPEAKING_PHRASE ||
                current.phase == LessonPhase.SPEAKING_FEEDBACK
            ) {
                return@enqueue
            }

            if (!current.phraseSpoken) {
                speakCurrentPhrase()
            } else {
                startRecording()
            }
        }
    }

    /** Next: skip current exercise and move on. */
    fun onNext() {
        logger.d(TAG, "AVRCP next index=${_state.value.phraseIndex}")
        enqueue {
            val current = _state.value
            if (!current.isActive || current.phrases.isEmpty()) return@enqueue
            if (current.phase == LessonPhase.COMPLETED) {
                speakFeedback("Урок уже завершён")
                return@enqueue
            }
            advanceToNext(announce = "Пропускаем. Следующее.")
        }
    }

    /** Previous: restart current phrase from the beginning. */
    fun onPrevious() {
        logger.d(TAG, "AVRCP previous index=${_state.value.phraseIndex}")
        enqueue {
            val current = _state.value
            if (!current.isActive || current.phrases.isEmpty()) return@enqueue
            if (current.phase == LessonPhase.COMPLETED) {
                speakFeedback("Урок уже завершён")
                return@enqueue
            }
            _state.update {
                it.copy(
                    phraseSpoken = false,
                    lastRecognizedText = null,
                    pronunciationScore = null,
                    phase = LessonPhase.IDLE,
                    statusMessage = "Повторим ещё раз",
                )
            }
            speakFeedback("Повторим ещё раз")
            speakCurrentPhrase()
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = scope.launch {
            commandMutex.withLock {
                block()
            }
        }
    }

    private suspend fun speakCurrentPhrase() {
        val current = _state.value
        val phrase = current.currentPhrase
        if (phrase.isBlank()) return

        _state.update {
            it.copy(
                phase = LessonPhase.SPEAKING_PHRASE,
                statusMessage = "Слушайте: $phrase",
            )
        }
        try {
            textToSpeech.speak(phrase, current.languageCode)
            _state.update {
                it.copy(
                    phraseSpoken = true,
                    phase = LessonPhase.READY_TO_RECORD,
                    statusMessage = "Play — записать ответ",
                )
            }
        } catch (error: Exception) {
            _state.update {
                it.copy(
                    phase = LessonPhase.IDLE,
                    statusMessage = error.message ?: "Ошибка озвучки",
                )
            }
        }
    }

    private suspend fun startRecording() {
        val current = _state.value
        if (!speechRecognizer.isAvailable()) {
            speakFeedback("Распознавание речи недоступно")
            return
        }

        _state.update {
            it.copy(
                phase = LessonPhase.SPEAKING_FEEDBACK,
                statusMessage = "Записываю",
            )
        }
        speakFeedback("Записываю")

        _state.update { it.copy(phase = LessonPhase.RECORDING, statusMessage = "Говорите…") }
        speechRecognizer.recognize(current.languageCode)
            .onSuccess { spoken ->
                val expected = _state.value.currentPhrase
                val score = PronunciationMatcher.similarity(expected, spoken)
                val matched = score >= 0.7f
                _state.update {
                    it.copy(
                        lastRecognizedText = spoken,
                        pronunciationScore = score,
                        phraseSpoken = false,
                        phase = LessonPhase.IDLE,
                    )
                }
                if (matched) {
                    advanceToNext(announce = "Верно, следующее")
                } else {
                    _state.update { it.copy(statusMessage = "Не совсем. Play — снова.") }
                    speakFeedback("Повторим ещё раз")
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        phase = LessonPhase.READY_TO_RECORD,
                        phraseSpoken = true,
                        statusMessage = error.message ?: "Не расслышал",
                    )
                }
                speakFeedback("Не расслышал, попробуй ещё раз")
            }
    }

    private suspend fun advanceToNext(announce: String) {
        val current = _state.value
        val nextIndex = current.phraseIndex + 1
        if (nextIndex >= current.phrases.size) {
            completeLesson(announce)
            return
        }

        _state.update {
            it.copy(
                phraseIndex = nextIndex,
                phraseSpoken = false,
                lastRecognizedText = null,
                pronunciationScore = null,
                phase = LessonPhase.IDLE,
                statusMessage = announce,
            )
        }
        speakFeedback(announce)
        speakCurrentPhrase()
    }

    private suspend fun completeLesson(announcePrefix: String) {
        val lessonId = _state.value.lessonId ?: return
        val score = _state.value.pronunciationScore
        if (!_state.value.isCompleted) {
            progressRepository.markLessonCompleted(lessonId, score)
        }
        _state.update {
            it.copy(
                isCompleted = true,
                phase = LessonPhase.COMPLETED,
                phraseSpoken = false,
                statusMessage = "Урок завершён",
            )
        }
        speakFeedback("$announcePrefix. Урок завершён")
    }

    private suspend fun speakFeedback(text: String) {
        val lang = _state.value.feedbackLanguageCode
        _state.update {
            it.copy(phase = LessonPhase.SPEAKING_FEEDBACK, statusMessage = text)
        }
        try {
            textToSpeech.speak(text, lang)
        } catch (_: Exception) {
            // Feedback is best-effort; session continues.
        }
    }

    companion object {
        private const val TAG = "LessonSession"
    }
}
