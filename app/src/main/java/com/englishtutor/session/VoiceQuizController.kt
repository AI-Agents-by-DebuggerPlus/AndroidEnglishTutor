package com.englishtutor.session

import com.englishtutor.data.stats.VoiceQuizAttemptStats
import com.englishtutor.data.stats.VoiceQuizSessionStats
import com.englishtutor.data.stats.VoiceQuizStatsStore
import com.englishtutor.domain.repository.ProgressRepository
import com.englishtutor.domain.util.PronunciationMatcher
import com.englishtutor.domain.voice.SpeechRecognizerProvider
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Headset-driven voice quiz from bank words matching the user's estimated level.
 * After each completed test, estimated level is recalculated.
 */
@Singleton
class VoiceQuizController @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val speechRecognizer: SpeechRecognizerProvider,
    private val statsStore: VoiceQuizStatsStore,
    private val progressRepository: ProgressRepository,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var job: Job? = null

    private var sessionId: String = ""
    private var sessionStartedAtMs: Long = 0L
    private val sessionAttempts = mutableListOf<VoiceQuizAttemptStats>()
    private var sessionPersisted = false
    private var sessionQuestions: List<VoiceQuizQuestion> = emptyList()

    private val _state = MutableStateFlow(VoiceQuizState())
    val state: StateFlow<VoiceQuizState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    fun activate() {
        job?.cancel()
        speechRecognizer.cancel()
        textToSpeech.stopSpeaking()
        rebuildSessionQuestions()
        beginNewSessionLocked()
        val level = statsStore.estimatedLevel()
        val guessed = statsStore.guessedWords().size
        val remaining = VoiceQuizBank.quizPool(
            statsStore.guessedWords(),
            level,
        ).size
        logger.i(
            TAG,
            "Quiz activate · ${sessionQuestions.size} questions · guessed=$guessed " +
                "remaining=$remaining level=$level · session=$sessionId",
        )
        val status = if (sessionQuestions.isEmpty()) {
            "Нет слов для теста на уровне $level."
        } else {
            "Уровень $level. Next — начать тест."
        }
        _state.value = VoiceQuizState(
            isActive = true,
            phase = VoiceQuizPhase.Idle,
            questionIndex = 0,
            questionCount = sessionQuestions.size,
            statusMessage = status,
            totalGuessedWords = guessed,
            remainingWords = remaining,
            estimatedLevel = level,
        )
    }

    private fun rebuildSessionQuestions() {
        val level = statsStore.estimatedLevel()
        sessionQuestions = VoiceQuizBank.pickQuizQuestions(
            guessed = statsStore.guessedWords(),
            level = level,
        )
    }

    private fun beginNewSessionLocked() {
        sessionId = UUID.randomUUID().toString()
        sessionStartedAtMs = System.currentTimeMillis()
        sessionAttempts.clear()
        sessionPersisted = false
    }

    fun deactivate() {
        job?.cancel()
        job = null
        speechRecognizer.cancel()
        textToSpeech.stopSpeaking()
        scope.launch {
            persistSessionIfNeeded(completed = false)
        }
        logger.i(TAG, "Quiz deactivate")
        _state.value = VoiceQuizState(isActive = false)
    }

    fun onNext(source: String = "native") {
        if (!isActive) return
        enqueue {
            when (_state.value.phase) {
                VoiceQuizPhase.Idle,
                VoiceQuizPhase.CorrectFeedback,
                -> askCurrentOrComplete(source)
                VoiceQuizPhase.AskQuestion -> {
                    logger.i(TAG, "Next ($source) → repeat question")
                    speakCurrentQuestion()
                }
                VoiceQuizPhase.WrongFeedback -> {
                    logger.i(TAG, "Next ($source) ignored — answer wrong; use Play")
                }
                VoiceQuizPhase.Listening -> {
                    logger.i(TAG, "Next ($source) ignored — listening")
                }
                VoiceQuizPhase.Completed -> {
                    logger.i(TAG, "Next ($source) → next test")
                    rebuildSessionQuestions()
                    beginNewSessionLocked()
                    val level = statsStore.estimatedLevel()
                    val guessed = statsStore.guessedWords().size
                    val remaining = VoiceQuizBank.quizPool(
                        statsStore.guessedWords(),
                        level,
                    ).size
                    if (sessionQuestions.isEmpty()) {
                        _state.value = VoiceQuizState(
                            isActive = true,
                            phase = VoiceQuizPhase.Idle,
                            questionIndex = 0,
                            questionCount = 0,
                            statusMessage = "Нет слов для теста на уровне $level.",
                            totalGuessedWords = guessed,
                            remainingWords = remaining,
                            estimatedLevel = level,
                        )
                        speakRu("Нет слов для теста.")
                        return@enqueue
                    }
                    _state.value = VoiceQuizState(
                        isActive = true,
                        phase = VoiceQuizPhase.Idle,
                        questionIndex = 0,
                        questionCount = sessionQuestions.size,
                        statusMessage = "Следующий тест (уровень $level). Play — ответить после вопроса.",
                        totalGuessedWords = guessed,
                        remainingWords = remaining,
                        estimatedLevel = level,
                    )
                    speakCurrentQuestion()
                }
            }
        }
    }

    fun onPlay(source: String = "native") {
        if (!isActive) return
        enqueue {
            when (_state.value.phase) {
                VoiceQuizPhase.AskQuestion,
                VoiceQuizPhase.WrongFeedback,
                -> startListening(source)
                VoiceQuizPhase.Listening -> {
                    logger.i(TAG, "Play ($source) → cancel STT")
                    speechRecognizer.cancel()
                    _state.update {
                        it.copy(
                            phase = VoiceQuizPhase.AskQuestion,
                            statusMessage = "Запись отменена. Play — ответить снова.",
                        )
                    }
                }
                VoiceQuizPhase.Idle -> {
                    logger.i(TAG, "Play ($source) ignored — press Next to start")
                }
                VoiceQuizPhase.CorrectFeedback -> {
                    logger.i(TAG, "Play ($source) ignored — press Next for next question")
                }
                VoiceQuizPhase.Completed -> {
                    logger.i(TAG, "Play ($source) ignored — quiz completed")
                }
            }
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        job?.cancel()
        textToSpeech.stopSpeaking()
        job = scope.launch {
            mutex.withLock { block() }
        }
    }

    private suspend fun askCurrentOrComplete(source: String) {
        if (sessionQuestions.isEmpty()) {
            rebuildSessionQuestions()
            if (sessionQuestions.isEmpty()) {
                speakRu("Нет слов для теста.")
                _state.update {
                    it.copy(
                        statusMessage = "Нет слов для теста на уровне ${statsStore.estimatedLevel()}.",
                        estimatedLevel = statsStore.estimatedLevel(),
                    )
                }
                return
            }
        }
        var index = _state.value.questionIndex
        if (_state.value.phase == VoiceQuizPhase.CorrectFeedback) {
            index += 1
            _state.update { it.copy(questionIndex = index) }
        }
        if (index >= sessionQuestions.size) {
            completeQuiz()
            return
        }
        logger.i(TAG, "Next ($source) → question ${index + 1}/${sessionQuestions.size}")
        speakCurrentQuestion()
    }

    private suspend fun speakCurrentQuestion() {
        if (sessionQuestions.isEmpty()) {
            rebuildSessionQuestions()
        }
        val index = _state.value.questionIndex.coerceIn(0, sessionQuestions.lastIndex.coerceAtLeast(0))
        val q = sessionQuestions.getOrNull(index) ?: return
        val level = statsStore.estimatedLevel()
        _state.update {
            it.copy(
                phase = VoiceQuizPhase.AskQuestion,
                questionIndex = index,
                questionCount = sessionQuestions.size,
                stimulusRu = q.stimulusRu,
                promptRu = q.promptRu(),
                lastSpoken = "",
                statusMessage = "Уровень $level · вопрос ${index + 1}/${sessionQuestions.size}. Play — ответить.",
                totalGuessedWords = statsStore.guessedWords().size,
                remainingWords = VoiceQuizBank.quizPool(
                    statsStore.guessedWords(),
                    level,
                ).size,
                estimatedLevel = level,
            )
        }
        speakRu(q.promptRu())
    }

    private suspend fun startListening(source: String) {
        val index = _state.value.questionIndex
        val q = sessionQuestions.getOrNull(index) ?: return
        logger.i(TAG, "Play ($source) → STT en-US for «${q.stimulusRu}»")
        speechRecognizer.cancel()
        textToSpeech.stopSpeaking()
        _state.update {
            it.copy(
                phase = VoiceQuizPhase.Listening,
                statusMessage = "Говорите ответ на английском…",
            )
        }
        val result = speechRecognizer.recognize(ANSWER_LANG)
        val spoken = result.getOrNull().orEmpty().trim()
        if (spoken.isEmpty()) {
            logger.w(TAG, "STT empty/fail: ${result.exceptionOrNull()?.message}")
            recordAttempt(q.stimulusRu, spoken = "", correct = false)
            _state.update {
                it.copy(
                    phase = VoiceQuizPhase.WrongFeedback,
                    lastSpoken = "",
                    statusMessage = "Не расслышала. Play — повторить ответ.",
                )
            }
            speakRu("Не правильно.")
            return
        }
        val matched = q.acceptedAnswersEn.any { expected ->
            PronunciationMatcher.isMatch(expected, spoken, MATCH_THRESHOLD)
        }
        logger.i(TAG, "STT «$spoken» match=$matched expected=${q.acceptedAnswersEn}")
        recordAttempt(q.stimulusRu, spoken = spoken, correct = matched)
        // Speak feedback first (before disk I/O) while A2DP route is still hot.
        if (matched) {
            _state.update {
                it.copy(
                    phase = VoiceQuizPhase.CorrectFeedback,
                    lastSpoken = spoken,
                    statusMessage = "Правильно! Next — следующий вопрос.",
                )
            }
            speakRu("Правильно!")
            statsStore.markGuessed(q.stimulusRu)
            val level = statsStore.estimatedLevel()
            _state.update {
                it.copy(
                    totalGuessedWords = statsStore.guessedWords().size,
                    remainingWords = VoiceQuizBank.quizPool(
                        statsStore.guessedWords(),
                        level,
                    ).size,
                    estimatedLevel = level,
                )
            }
        } else {
            _state.update {
                it.copy(
                    phase = VoiceQuizPhase.WrongFeedback,
                    lastSpoken = spoken,
                    statusMessage = "Не правильно («$spoken»). Play — ещё раз.",
                )
            }
            speakRu("Не правильно.")
        }
    }

    private fun recordAttempt(stimulusRu: String, spoken: String, correct: Boolean) {
        sessionAttempts += VoiceQuizAttemptStats(
            stimulusRu = stimulusRu,
            spoken = spoken,
            correct = correct,
            atMs = System.currentTimeMillis(),
        )
    }

    private suspend fun completeQuiz() {
        logger.i(TAG, "Quiz completed")
        persistSessionIfNeeded(completed = true)
        val level = statsStore.estimatedLevel()
        val guessed = statsStore.guessedWords().size
        val remaining = VoiceQuizBank.quizPool(
            statsStore.guessedWords(),
            level,
        ).size
        _state.update {
            it.copy(
                phase = VoiceQuizPhase.Completed,
                stimulusRu = "",
                promptRu = "",
                statusMessage = "Тест завершён. Уровень: $level. Отгадано слов: $guessed. Next — следующий тест.",
                totalGuessedWords = guessed,
                remainingWords = remaining,
                estimatedLevel = level,
            )
        }
        speakRu("Тест завершён. Ваш уровень $level. Нажмите Next для следующего теста.")
    }

    private suspend fun persistSessionIfNeeded(completed: Boolean) {
        if (sessionPersisted) return
        if (sessionAttempts.isEmpty() && !completed) return
        val session = VoiceQuizSessionStats(
            id = sessionId.ifBlank { UUID.randomUUID().toString() },
            startedAtMs = sessionStartedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            completedAtMs = if (completed) System.currentTimeMillis() else null,
            completed = completed,
            questionsTotal = sessionQuestions.size.coerceAtLeast(_state.value.questionCount),
            attempts = sessionAttempts.toList(),
        )
        runCatching {
            val updated = statsStore.appendSession(session)
            sessionPersisted = true
            if (completed) {
                val level = updated.estimatedLevel
                val score = if (session.attempts.isEmpty()) {
                    0
                } else {
                    ((session.correctAnswers.toFloat() / session.attempts.size) * 100).toInt()
                }
                progressRepository.savePlacementResult(
                    level = level,
                    score = score,
                    details = "voice-quiz-level:${session.id}",
                )
                logger.i(TAG, "User level updated → $level score=$score")
            }
        }.onFailure { error ->
            logger.e(TAG, "Stats save failed: ${error.message}")
        }
    }

    private suspend fun speakRu(text: String) {
        runCatching {
            textToSpeech.speak(text, PROMPT_LANG)
        }.onFailure { error ->
            logger.e(TAG, "TTS failed: ${error.message}")
        }
    }

    companion object {
        private const val TAG = "VoiceQuiz"
        private const val PROMPT_LANG = "ru-RU"
        private const val ANSWER_LANG = "en-US"
        private const val MATCH_THRESHOLD = 0.7f
    }
}
