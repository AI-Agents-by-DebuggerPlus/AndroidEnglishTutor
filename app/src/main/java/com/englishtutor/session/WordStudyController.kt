package com.englishtutor.session

import com.englishtutor.data.stats.VoiceQuizStatsStore
import com.englishtutor.data.topics.StudyCard
import com.englishtutor.data.topics.StudyStage
import com.englishtutor.data.topics.TopicInfo
import com.englishtutor.data.topics.TopicRepository
import com.englishtutor.data.voice.StudySessionPreferences
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
    AllComplete,
}

data class WordStudyState(
    val isActive: Boolean = false,
    val phase: WordStudyPhase = WordStudyPhase.Idle,
    val topicId: String = "",
    val topicTitle: String = "",
    val topics: List<TopicInfo> = emptyList(),
    val stage: StudyStage = StudyStage.Words,
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
    val studiedCount: Int = 0,
    val studiedWordsCount: Int = 0,
    val studiedPhrasesCount: Int = 0,
    val studiedSentencesCount: Int = 0,
    val viewedTopicCount: Int = 0,
    val topicCount: Int = 0,
    /** Bumped on every show/replay so UI can un-blank. */
    val displayEpoch: Long = 0L,
)

/**
 * Topic-based study: 10 words → 10 phrases → 10 sentences → next words.
 * TTS: EN → RU → EN → EN. Progress persists across restarts.
 */
@Singleton
class WordStudyController @Inject constructor(
    private val textToSpeech: TextToSpeechProvider,
    private val statsStore: VoiceQuizStatsStore,
    private val topicRepository: TopicRepository,
    private val sessionPrefs: StudySessionPreferences,
    private val logger: AppLogger,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var job: Job? = null

    private var queue: List<StudyCard> = emptyList()
    private var batchWordEns: List<String> = emptyList()
    private var currentTopicId: String = ""
    private var currentStage: StudyStage = StudyStage.Words

    private val _state = MutableStateFlow(WordStudyState())
    val state: StateFlow<WordStudyState> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.isActive

    fun activate(autoSpeak: Boolean = false) {
        job?.cancel()
        textToSpeech.stopSpeaking()
        val topics = topicRepository.listTopics()
        currentTopicId = sessionPrefs.topicId(topicRepository.defaultTopicId())
        currentStage = sessionPrefs.stage()
        batchWordEns = sessionPrefs.batchWordEns()
        // Prefer not-yet-viewed topics on a fresh start of a topic.
        val viewed = sessionPrefs.viewedTopicIds()
        val unviewedIds = topics.map { it.id }.filter { it !in viewed }
        if (
            unviewedIds.isNotEmpty() &&
            currentTopicId !in unviewedIds &&
            currentStage == StudyStage.Words &&
            sessionPrefs.cardIndex() == 0 &&
            sessionPrefs.batchWordEns().isEmpty()
        ) {
            currentTopicId = unviewedIds.first()
            logger.i(TAG, "Activate → first unviewed topic $currentTopicId")
        }
        rebuildQueue()
        val savedIndex = sessionPrefs.cardIndex().coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        val topic = topicRepository.getTopic(currentTopicId)
        val level = statsStore.estimatedLevel()
        logger.i(
            TAG,
            "Study activate · topic=$currentTopicId stage=$currentStage " +
                "queue=${queue.size} index=$savedIndex topics=${topics.size} " +
                "studied=${sessionPrefs.studiedCount()} autoSpeak=$autoSpeak",
        )
        _state.value = WordStudyState(
            isActive = true,
            phase = WordStudyPhase.Idle,
            topicId = currentTopicId,
            topicTitle = topic?.info?.titleRu.orEmpty(),
            topics = topics,
            stage = currentStage,
            lessonIndex = 0,
            lessonCount = 1,
            wordIndex = savedIndex,
            wordCount = queue.size,
            statusMessage = when {
                topics.isEmpty() -> "Темы не загружены."
                queue.isEmpty() -> "Нет карточек в теме «${topic?.info?.titleRu}»."
                else -> "Тема: ${topic?.info?.titleRu} · ${currentStage.labelRu}. Play — озвучить."
            },
            totalGuessedWords = statsStore.guessedWords().size,
            unguessedWords = VoiceQuizBank.studyPool(statsStore.guessedWords(), level).size,
            estimatedLevel = level,
            studiedCount = sessionPrefs.studiedCount(),
            studiedWordsCount = sessionPrefs.studiedWordsCount(),
            studiedPhrasesCount = sessionPrefs.studiedPhrasesCount(),
            studiedSentencesCount = sessionPrefs.studiedSentencesCount(),
            viewedTopicCount = sessionPrefs.viewedTopicIds().size,
            topicCount = topics.size,
        )
        val card = queue.getOrNull(savedIndex)
        if (card != null) {
            sessionPrefs.markTopicViewed(currentTopicId)
            _state.update {
                it.copy(
                    phase = WordStudyPhase.ShowingWord,
                    english = card.en,
                    russian = card.ru,
                    wordIndex = savedIndex,
                    displayEpoch = it.displayEpoch + 1,
                    statusMessage = statusFor(card, savedIndex),
                    viewedTopicCount = sessionPrefs.viewedTopicIds().size,
                )
            }
            if (autoSpeak) {
                // Speak current card after entering study from Home / Next.
                enqueue {
                    showCard(savedIndex, markStudied = false)
                }
            }
        }
    }

    fun deactivate() {
        job?.cancel()
        job = null
        textToSpeech.stopSpeaking()
        persistPosition()
        logger.i(TAG, "Study deactivate")
        _state.value = WordStudyState(
            isActive = false,
            topics = topicRepository.listTopics(),
            topicId = currentTopicId,
            topicTitle = topicRepository.getTopic(currentTopicId)?.info?.titleRu.orEmpty(),
            estimatedLevel = statsStore.estimatedLevel(),
            studiedCount = sessionPrefs.studiedCount(),
            studiedWordsCount = sessionPrefs.studiedWordsCount(),
            studiedPhrasesCount = sessionPrefs.studiedPhrasesCount(),
            studiedSentencesCount = sessionPrefs.studiedSentencesCount(),
            viewedTopicCount = sessionPrefs.viewedTopicIds().size,
            topicCount = topicRepository.listTopics().size,
        )
    }

    fun refreshStudyStats() {
        _state.update {
            it.copy(
                studiedCount = sessionPrefs.studiedCount(),
                studiedWordsCount = sessionPrefs.studiedWordsCount(),
                studiedPhrasesCount = sessionPrefs.studiedPhrasesCount(),
                studiedSentencesCount = sessionPrefs.studiedSentencesCount(),
                viewedTopicCount = sessionPrefs.viewedTopicIds().size,
                topicCount = topicRepository.listTopics().size,
                topics = topicRepository.listTopics(),
            )
        }
    }

    /** Stop TTS only — keep study armed (e.g. while opening voice picker). */
    fun pauseSpeaking() {
        job?.cancel()
        job = null
        textToSpeech.stopSpeaking()
        persistPosition()
        logger.i(TAG, "Study pause speaking (stays active=$isActive)")
    }

    fun selectTopic(topicId: String) {
        if (!isActive) return
        enqueue {
            logger.i(TAG, "Topic selected → $topicId")
            selectTopicLocked(topicId, autoShow = false)
        }
    }

    fun nextTopic(source: String = "native") {
        if (!isActive) return
        enqueue {
            val topics = topicRepository.listTopics()
            if (topics.isEmpty()) return@enqueue
            val ids = topics.map { it.id }
            sessionPrefs.markTopicViewed(currentTopicId)
            val nextId = sessionPrefs.nextTopicId(currentTopicId, ids) ?: ids.first()
            logger.i(
                TAG,
                "Next topic ($source) → $nextId · viewed=${sessionPrefs.viewedTopicIds().size}/${ids.size}",
            )
            selectTopicLocked(nextId, autoShow = true)
        }
    }

    fun onNext(source: String = "native") {
        if (!isActive) {
            logger.w(TAG, "Next ($source) while inactive → activate")
            activate()
        }
        if (!isActive) return
        enqueue {
            when (_state.value.phase) {
                WordStudyPhase.Idle -> {
                    if (queue.isEmpty()) rebuildQueue()
                    if (queue.isEmpty()) {
                        speakRu("Нет карточек.")
                        return@enqueue
                    }
                    val index = sessionPrefs.cardIndex().coerceIn(0, queue.lastIndex)
                    showCard(index)
                }
                WordStudyPhase.ShowingWord -> {
                    val nextIndex = _state.value.wordIndex + 1
                    if (nextIndex < queue.size) {
                        showCard(nextIndex)
                    } else {
                        advanceStageOrBatch()
                    }
                }
                WordStudyPhase.AllComplete -> {
                    rebuildQueue()
                    if (queue.isEmpty()) {
                        speakRu("Нет карточек.")
                        return@enqueue
                    }
                    showCard(0)
                }
            }
        }
    }

    fun onPlay(source: String = "native") {
        if (!isActive) {
            logger.w(TAG, "Play ($source) while inactive → activate")
            activate()
        }
        if (!isActive) return
        enqueue {
            if (queue.isEmpty()) rebuildQueue()
            when (_state.value.phase) {
                WordStudyPhase.ShowingWord -> {
                    val idx = _state.value.wordIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
                    logger.i(TAG, "Play ($source) → replay ${idx + 1}/${queue.size}")
                    if (queue.isEmpty()) {
                        speakRu("Нет карточек.")
                        return@enqueue
                    }
                    showCard(idx, markStudied = false)
                }
                WordStudyPhase.Idle, WordStudyPhase.AllComplete -> {
                    if (queue.isEmpty()) {
                        speakRu("Нет карточек.")
                        return@enqueue
                    }
                    val index = sessionPrefs.cardIndex().coerceIn(0, queue.lastIndex)
                    logger.i(TAG, "Play ($source) → start/show ${index + 1}")
                    showCard(index, markStudied = false)
                }
            }
        }
    }

    private suspend fun selectTopicLocked(topicId: String, autoShow: Boolean) {
        val topic = topicRepository.getTopic(topicId) ?: return
        currentTopicId = topic.info.id
        currentStage = StudyStage.Words
        batchWordEns = emptyList()
        rebuildQueue()
        persistPosition(index = 0)
        sessionPrefs.markTopicViewed(currentTopicId)
        _state.update {
            it.copy(
                topicId = currentTopicId,
                topicTitle = topic.info.titleRu,
                stage = currentStage,
                phase = WordStudyPhase.Idle,
                english = "",
                russian = "",
                wordIndex = 0,
                wordCount = queue.size,
                statusMessage = "Тема: ${topic.info.titleRu}. Next — первое слово.",
                displayEpoch = it.displayEpoch + 1,
                topics = topicRepository.listTopics(),
                viewedTopicCount = sessionPrefs.viewedTopicIds().size,
                topicCount = topicRepository.listTopics().size,
                studiedWordsCount = sessionPrefs.studiedWordsCount(),
            )
        }
        speakRu("Тема ${topic.info.titleRu}")
        if (autoShow && queue.isNotEmpty()) {
            showCard(0)
        }
    }

    private suspend fun advanceStageOrBatch() {
        when (currentStage) {
            StudyStage.Words -> {
                // After a batch of words → phrases for those words.
                if (batchWordEns.isEmpty() && queue.isNotEmpty()) {
                    batchWordEns = queue.mapNotNull { it.uses.firstOrNull() }.distinct()
                }
                currentStage = StudyStage.Phrases
                rebuildQueue()
                if (queue.isEmpty()) {
                    currentStage = StudyStage.Sentences
                    rebuildQueue()
                }
                if (queue.isEmpty()) {
                    startNextWordBatch()
                    return
                }
                logger.i(TAG, "Advance → phrases (${queue.size})")
                speakRu("Словосочетания")
                showCard(0)
            }
            StudyStage.Phrases -> {
                currentStage = StudyStage.Sentences
                rebuildQueue()
                if (queue.isEmpty()) {
                    startNextWordBatch()
                    return
                }
                logger.i(TAG, "Advance → sentences (${queue.size})")
                speakRu("Предложения")
                showCard(0)
            }
            StudyStage.Sentences -> {
                startNextWordBatch()
            }
        }
    }

    private suspend fun startNextWordBatch() {
        currentStage = StudyStage.Words
        batchWordEns = emptyList()
        rebuildQueue()
        if (queue.isEmpty()) {
            sessionPrefs.markTopicViewed(currentTopicId)
            _state.update {
                it.copy(
                    phase = WordStudyPhase.AllComplete,
                    english = "",
                    russian = "",
                    statusMessage = "Тема пройдена. 3×Play — следующая тема, или выберите тему.",
                    displayEpoch = it.displayEpoch + 1,
                    viewedTopicCount = sessionPrefs.viewedTopicIds().size,
                )
            }
            persistPosition(index = 0)
            speakRu("Тема пройдена.")
            return
        }
        logger.i(TAG, "Advance → next word batch (${queue.size})")
        speakRu("Следующие слова")
        showCard(0)
    }

    private fun rebuildQueue() {
        val topic = topicRepository.getTopic(currentTopicId) ?: run {
            queue = emptyList()
            return
        }
        currentTopicId = topic.info.id
        queue = when (currentStage) {
            StudyStage.Words -> {
                val batch = pickUnstudiedFirst(topic.words, preferredUses = emptySet(), matchUses = false)
                batchWordEns = batch.mapNotNull { it.uses.firstOrNull() }.distinct()
                batch
            }
            StudyStage.Phrases -> {
                pickUnstudiedFirst(topic.phrases, preferredUses = batchWordEns.toSet(), matchUses = true)
            }
            StudyStage.Sentences -> {
                pickUnstudiedFirst(topic.sentences, preferredUses = batchWordEns.toSet(), matchUses = true)
            }
        }
    }

    /**
     * Prefer cards not yet marked studied. For phrases/sentences, also prefer those
     * linked to the current word batch via [StudyCard.uses]; fall back to any unstudied,
     * then finally recycle already-studied cards.
     */
    private fun pickUnstudiedFirst(
        cards: List<StudyCard>,
        preferredUses: Set<String>,
        matchUses: Boolean,
    ): List<StudyCard> {
        if (cards.isEmpty()) return emptyList()
        val unstudied = cards.filterNot { sessionPrefs.isStudied(it.progressKey) }
        val pool = unstudied.ifEmpty { cards }
        if (!matchUses || preferredUses.isEmpty()) {
            return pool.take(BATCH_SIZE)
        }
        val matchedUnstudied = unstudied.filter { card -> card.uses.any { it in preferredUses } }
        if (matchedUnstudied.isNotEmpty()) {
            return matchedUnstudied.take(BATCH_SIZE)
        }
        val matchedAny = pool.filter { card -> card.uses.any { it in preferredUses } }
        if (matchedAny.isNotEmpty()) {
            return matchedAny.take(BATCH_SIZE)
        }
        return pool.take(BATCH_SIZE)
    }

    private suspend fun showCard(index: Int, markStudied: Boolean = true) {
        val card = queue.getOrNull(index) ?: return
        if (currentStage == StudyStage.Words && batchWordEns.isEmpty()) {
            batchWordEns = queue.mapNotNull { it.uses.firstOrNull() }.distinct()
        }
        persistPosition(index)
        val level = statsStore.estimatedLevel()
        _state.update {
            it.copy(
                phase = WordStudyPhase.ShowingWord,
                topicId = currentTopicId,
                topicTitle = topicRepository.getTopic(currentTopicId)?.info?.titleRu.orEmpty(),
                topics = topicRepository.listTopics(),
                stage = currentStage,
                lessonIndex = 0,
                lessonCount = 1,
                wordIndex = index,
                wordCount = queue.size,
                english = card.en,
                russian = card.ru,
                statusMessage = statusFor(card, index),
                totalGuessedWords = statsStore.guessedWords().size,
                unguessedWords = VoiceQuizBank.studyPool(statsStore.guessedWords(), level).size,
                estimatedLevel = level,
                studiedCount = sessionPrefs.studiedCount(),
                displayEpoch = it.displayEpoch + 1,
            )
        }
        speakEn(card.en)
        delay(GAP_MS)
        speakRu(card.ru)
        delay(GAP_MS)
        speakEn(card.en)
        delay(GAP_MS)
        speakEn(card.en)
        sessionPrefs.markTopicViewed(currentTopicId)
        if (markStudied) {
            sessionPrefs.markStudied(card.progressKey)
            statsStore.markPlayed(card.ru)
            logger.i(TAG, "Studied «${card.ru}» (${card.stage}) · total=${sessionPrefs.studiedCount()}")
            _state.update {
                it.copy(
                    studiedCount = sessionPrefs.studiedCount(),
                    studiedWordsCount = sessionPrefs.studiedWordsCount(),
                    studiedPhrasesCount = sessionPrefs.studiedPhrasesCount(),
                    studiedSentencesCount = sessionPrefs.studiedSentencesCount(),
                    viewedTopicCount = sessionPrefs.viewedTopicIds().size,
                )
            }
        }
    }

    private fun statusFor(card: StudyCard, index: Int): String =
        "${_state.value.topicTitle.ifBlank { currentTopicId }} · ${currentStage.labelRu} " +
            "${index + 1}/${queue.size}: ${card.en}"

    private fun persistPosition(index: Int = _state.value.wordIndex) {
        sessionPrefs.savePosition(
            topicId = currentTopicId,
            stage = currentStage,
            cardIndex = index,
            batchWordEns = batchWordEns,
        )
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
        const val BATCH_SIZE = 10
    }
}
