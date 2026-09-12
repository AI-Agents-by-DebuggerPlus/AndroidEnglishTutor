package com.englishtutor.session

import com.englishtutor.data.stats.VoiceQuizStatsStore
import com.englishtutor.domain.voice.TextToSpeechProvider
import com.englishtutor.util.AppLogger
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

enum class WordStudyPhase {
    Idle,
    ShowingWord,
    LessonComplete,
    AllComplete,
}

data class WordStudyState(
    val isActive: Boolean = false,
    val phase: WordStudyPhase = WordStudyPhase.Idle,
    val lessonIndex: Int = 0,
    val lessonCount: Int = 0,
    val wordIndex: Int = 0,
    val wordCount: Int = 0,
    val english: String = "",
    val russian: String = "",
    val statusMessage: String = "Нажмите Next, чтобы начать урок.",
    val totalGuessedWords: Int = 0,
    val unguessedWords: Int = 0,
    val estimatedLevel: String = "A1",
)

/**
 * Study new / unguessed words. TTS: EN → RU → EN → EN.
 * After playback, word is marked played (eligible for tests).
 */
@Singleton
class WordStudyController @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val statsStore: VoiceQuizStatsStore,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var job: Job? = null

    private var lessons: List<List<VoiceQuizQuestion>> = emptyList()

    private val _state = MutableStateFlow(WordStudyState())
    val state: StateFlow<WordStudyState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    fun activate() {
        job?.cancel()
        textToSpeech.stopSpeaking()
        rebuildLessons()
        val level = statsStore.estimatedLevel()
        val guessed = statsStore.guessedWords().size
        val unguessed = VoiceQuizBank.studyPool(statsStore.guessedWords(), level).size
        logger.i(TAG, "Study activate · lessons=${lessons.size} unguessed=$unguessed level=$level")
        _state.value = WordStudyState(
            isActive = true,
            phase = WordStudyPhase.Idle,
            lessonIndex = 0,
            lessonCount = lessons.size,
            wordIndex = 0,
            wordCount = lessons.firstOrNull()?.size ?: 0,
            statusMessage = if (lessons.isEmpty()) {
                "Нет слов для изучения на уровне $level."
            } else {
                "Уровень $level · уроков: ${lessons.size}. Next — первое слово."
            },
            totalGuessedWords = guessed,
            unguessedWords = unguessed,
            estimatedLevel = level,
        )
    }

    fun deactivate() {
        job?.cancel()
        job = null
        textToSpeech.stopSpeaking()
        logger.i(TAG, "Study deactivate")
        _state.value = WordStudyState(isActive = false)
    }

    fun rebuildLessons() {
        val level = statsStore.estimatedLevel()
        lessons = VoiceQuizBank.buildStudyLessons(statsStore.guessedWords(), level)
    }

    fun onNext(source: String = "native") {
        if (!isActive) return
        enqueue {
            when (_state.value.phase) {
                WordStudyPhase.Idle -> {
                    if (lessons.isEmpty()) {
                        rebuildLessons()
                    }
                    if (lessons.isEmpty()) {
                        val level = statsStore.estimatedLevel()
                        _state.update {
                            it.copy(
                                phase = WordStudyPhase.AllComplete,
                                statusMessage = "Нет слов для изучения на уровне $level.",
                                estimatedLevel = level,
                            )
                        }
                        speakRu("Нет новых слов.")
                        return@enqueue
                    }
                    logger.i(TAG, "Next ($source) → start lesson 1")
                    showWord(lessonIndex = 0, wordIndex = 0)
                }
                WordStudyPhase.ShowingWord -> {
                    val lessonIdx = _state.value.lessonIndex
                    val wordIdx = _state.value.wordIndex + 1
                    val lesson = lessons.getOrNull(lessonIdx).orEmpty()
                    if (wordIdx < lesson.size) {
                        logger.i(TAG, "Next ($source) → word ${wordIdx + 1}/${lesson.size}")
                        showWord(lessonIdx, wordIdx)
                    } else {
                        val nextLesson = lessonIdx + 1
                        if (nextLesson < lessons.size) {
                            logger.i(TAG, "Next ($source) → lesson ${nextLesson + 1}")
                            showWord(nextLesson, 0)
                        } else {
                            logger.i(TAG, "Next ($source) → all lessons done")
                            _state.update {
                                it.copy(
                                    phase = WordStudyPhase.AllComplete,
                                    english = "",
                                    russian = "",
                                    statusMessage = "Все уроки пройдены. Next — начать сначала.",
                                )
                            }
                            speakRu("Все уроки пройдены.")
                        }
                    }
                }
                WordStudyPhase.LessonComplete -> {
                    val nextLesson = _state.value.lessonIndex + 1
                    if (nextLesson < lessons.size) {
                        showWord(nextLesson, 0)
                    } else {
                        _state.update {
                            it.copy(
                                phase = WordStudyPhase.AllComplete,
                                statusMessage = "Все уроки пройдены. Next — начать сначала.",
                            )
                        }
                    }
                }
                WordStudyPhase.AllComplete -> {
                    rebuildLessons()
                    if (lessons.isEmpty()) {
                        speakRu("Нет новых слов.")
                        return@enqueue
                    }
                    showWord(0, 0)
                }
            }
        }
    }

    fun onPlay(source: String = "native") {
        if (!isActive) return
        enqueue {
            when (_state.value.phase) {
                WordStudyPhase.ShowingWord -> {
                    val lessonIdx = _state.value.lessonIndex
                    val wordIdx = _state.value.wordIndex
                    logger.i(TAG, "Play ($source) → replay word ${wordIdx + 1}")
                    showWord(lessonIdx, wordIdx, markPlayed = false)
                }
                else -> {
                    logger.i(TAG, "Play ($source) ignored — сначала Next, чтобы показать слово")
                }
            }
        }
    }

    private suspend fun showWord(
        lessonIndex: Int,
        wordIndex: Int,
        markPlayed: Boolean = true,
    ) {
        val lesson = lessons.getOrNull(lessonIndex) ?: return
        val word = lesson.getOrNull(wordIndex) ?: return
        val level = statsStore.estimatedLevel()
        _state.update {
            it.copy(
                phase = WordStudyPhase.ShowingWord,
                lessonIndex = lessonIndex,
                lessonCount = lessons.size,
                wordIndex = wordIndex,
                wordCount = lesson.size,
                english = word.primaryEn,
                russian = word.stimulusRu,
                statusMessage = "Уровень $level · урок ${lessonIndex + 1}/${lessons.size} · слово ${wordIndex + 1}/${lesson.size}. Play — повторить.",
                totalGuessedWords = statsStore.guessedWords().size,
                unguessedWords = VoiceQuizBank.studyPool(statsStore.guessedWords(), level).size,
                estimatedLevel = level,
            )
        }
        // EN → RU → EN → EN
        speakEn(word.primaryEn)
        delay(GAP_MS)
        speakRu(word.stimulusRu)
        delay(GAP_MS)
        speakEn(word.primaryEn)
        delay(GAP_MS)
        speakEn(word.primaryEn)
        if (markPlayed) {
            statsStore.markPlayed(word.stimulusRu)
            logger.i(TAG, "Word played «${word.stimulusRu}» → eligible for tests")
        }
    }

    private fun enqueue(block: suspend () -> Unit) {
        job?.cancel()
        textToSpeech.stopSpeaking()
        job = scope.launch {
            mutex.withLock { block() }
        }
    }

    private suspend fun speakEn(text: String) {
        runCatching { textToSpeech.speak(text, EN_LANG) }
            .onFailure { logger.e(TAG, "TTS EN failed: ${it.message}") }
    }

    private suspend fun speakRu(text: String) {
        runCatching { textToSpeech.speak(text, RU_LANG) }
            .onFailure { logger.e(TAG, "TTS RU failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "WordStudy"
        private const val EN_LANG = "en-US"
        private const val RU_LANG = "ru-RU"
        private const val GAP_MS = 280L
    }
}
