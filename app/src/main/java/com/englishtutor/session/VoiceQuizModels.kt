package com.englishtutor.session

/**
 * One voice-quiz item: Russian prompt stimulus + acceptable English answers + CEFR-ish level.
 */
data class VoiceQuizQuestion(
    val stimulusRu: String,
    val acceptedAnswersEn: List<String>,
    val level: String = "A1",
) {
    fun promptRu(): String =
        "Как на английском сказать: \"$stimulusRu\"?"

    /** Primary English form for study cards. */
    val primaryEn: String get() = acceptedAnswersEn.firstOrNull().orEmpty()
}

object VoiceQuizBank {
    const val QUESTIONS_PER_TEST: Int = 5
    const val WORDS_PER_STUDY_LESSON: Int = 8

    val levels: List<String> = listOf("A1", "A2", "B1")

    val questions: List<VoiceQuizQuestion> = listOf(
        // A1 — базовые фразы и слова
        q("Привет", listOf("hello", "hi"), "A1"),
        q("Спасибо", listOf("thank you", "thanks"), "A1"),
        q("Пока", listOf("bye", "goodbye", "good bye"), "A1"),
        q("Да", listOf("yes", "yeah"), "A1"),
        q("Нет", listOf("no", "nope"), "A1"),
        q("Пожалуйста", listOf("please"), "A1"),
        q("Извините", listOf("sorry", "excuse me"), "A1"),
        q("Доброе утро", listOf("good morning"), "A1"),
        q("Добрый день", listOf("good afternoon", "good day"), "A1"),
        q("Добрый вечер", listOf("good evening"), "A1"),
        q("Спокойной ночи", listOf("good night"), "A1"),
        q("Вода", listOf("water"), "A1"),
        q("Еда", listOf("food"), "A1"),
        q("Хлеб", listOf("bread"), "A1"),
        q("Молоко", listOf("milk"), "A1"),
        q("Кофе", listOf("coffee"), "A1"),
        q("Чай", listOf("tea"), "A1"),
        q("Дом", listOf("house", "home"), "A1"),
        q("Школа", listOf("school"), "A1"),
        q("Книга", listOf("book"), "A1"),
        q("Один", listOf("one"), "A1"),
        q("Два", listOf("two"), "A1"),
        q("Три", listOf("three"), "A1"),
        // A2 — быт, семья, описания
        q("Как дела?", listOf("how are you", "how are you doing"), "A2"),
        q("Меня зовут", listOf("my name is"), "A2"),
        q("Рад познакомиться", listOf("nice to meet you", "pleased to meet you"), "A2"),
        q("До свидания", listOf("goodbye", "good bye", "farewell"), "A2"),
        q("Стол", listOf("table"), "A2"),
        q("Стул", listOf("chair"), "A2"),
        q("Окно", listOf("window"), "A2"),
        q("Дверь", listOf("door"), "A2"),
        q("Машина", listOf("car"), "A2"),
        q("Город", listOf("city", "town"), "A2"),
        q("Улица", listOf("street", "road"), "A2"),
        q("Друг", listOf("friend"), "A2"),
        q("Семья", listOf("family"), "A2"),
        q("Мама", listOf("mom", "mum", "mother"), "A2"),
        q("Папа", listOf("dad", "father"), "A2"),
        q("Ребёнок", listOf("child", "kid"), "A2"),
        q("Мужчина", listOf("man"), "A2"),
        q("Женщина", listOf("woman"), "A2"),
        q("Большой", listOf("big", "large"), "A2"),
        q("Маленький", listOf("small", "little"), "A2"),
        q("Хороший", listOf("good"), "A2"),
        q("Плохой", listOf("bad"), "A2"),
        q("Новый", listOf("new"), "A2"),
        q("Старый", listOf("old"), "A2"),
        q("Красный", listOf("red"), "A2"),
        q("Синий", listOf("blue"), "A2"),
        q("Зелёный", listOf("green"), "A2"),
        q("Белый", listOf("white"), "A2"),
        q("Чёрный", listOf("black"), "A2"),
        q("Сегодня", listOf("today"), "A2"),
        q("Завтра", listOf("tomorrow"), "A2"),
        q("Вчера", listOf("yesterday"), "A2"),
        q("Сейчас", listOf("now"), "A2"),
        q("Здесь", listOf("here"), "A2"),
        q("Там", listOf("there"), "A2"),
        // B1 — фразы / потребности
        q("Люблю", listOf("love", "i love"), "B1"),
        q("Хочу", listOf("want", "i want"), "B1"),
        q("Нужно", listOf("need", "i need"), "B1"),
        q("Помогите", listOf("help", "help me"), "B1"),
        q("Я не понимаю", listOf("i don't understand", "i do not understand"), "B1"),
        q("Повторите пожалуйста", listOf("repeat please", "please repeat"), "B1"),
        q("Сколько это стоит?", listOf("how much is it", "how much does it cost"), "B1"),
        q("Где туалет?", listOf("where is the bathroom", "where is the toilet"), "B1"),
    )

    private fun q(stimulusRu: String, answers: List<String>, level: String) =
        VoiceQuizQuestion(stimulusRu, answers, level)

    fun byStimulus(stimulusRu: String): VoiceQuizQuestion? =
        questions.firstOrNull { it.stimulusRu == stimulusRu }

    fun levelIndex(level: String): Int =
        levels.indexOf(level.uppercase()).coerceAtLeast(0)

    fun levelsAtOrBelow(level: String): Set<String> {
        val idx = levelIndex(level)
        return levels.take(idx + 1).toSet()
    }

    /** Words for lessons: not yet mastered (unguessed), within current difficulty. */
    fun studyPool(guessed: Set<String>, level: String): List<VoiceQuizQuestion> {
        val allowed = levelsAtOrBelow(level)
        val pool = questions.filter {
            it.level in allowed && it.stimulusRu !in guessed
        }
        return pool.ifEmpty {
            questions.filter { it.stimulusRu !in guessed }
        }.ifEmpty { emptyList() }
    }

    /**
     * Words for tests: any bank words matching the user's current level
     * (prefer exact level, then at-or-below), excluding already guessed.
     * Not limited to words heard in lessons.
     */
    fun quizPool(guessed: Set<String>, level: String): List<VoiceQuizQuestion> {
        val normalized = level.trim().uppercase().ifBlank { "A1" }
        val exactUnguessed = questions.filter {
            it.level.equals(normalized, ignoreCase = true) && it.stimulusRu !in guessed
        }
        if (exactUnguessed.isNotEmpty()) return exactUnguessed

        val allowed = levelsAtOrBelow(normalized)
        val belowUnguessed = questions.filter {
            it.level in allowed && it.stimulusRu !in guessed
        }
        if (belowUnguessed.isNotEmpty()) return belowUnguessed

        // All at level already guessed — allow re-practice of that level.
        val exactAll = questions.filter { it.level.equals(normalized, ignoreCase = true) }
        return exactAll.ifEmpty { questions.filter { it.level in allowed } }
    }

    fun pickQuizQuestions(
        guessed: Set<String>,
        level: String,
        count: Int = QUESTIONS_PER_TEST,
    ): List<VoiceQuizQuestion> {
        val pool = quizPool(guessed, level)
        if (pool.isEmpty()) return emptyList()
        return pool.shuffled().take(count.coerceAtLeast(1).coerceAtMost(pool.size))
    }

    fun buildStudyLessons(
        guessed: Set<String>,
        level: String,
        wordsPerLesson: Int = WORDS_PER_STUDY_LESSON,
    ): List<List<VoiceQuizQuestion>> {
        val pool = studyPool(guessed, level)
        if (pool.isEmpty()) return emptyList()
        return pool.chunked(wordsPerLesson.coerceAtLeast(1))
    }

    @Deprecated("Use studyPool / quizPool")
    fun unused(guessed: Set<String>): List<VoiceQuizQuestion> =
        questions.filter { it.stimulusRu !in guessed }
}

enum class VoiceQuizPhase {
    Idle,
    AskQuestion,
    Listening,
    CorrectFeedback,
    WrongFeedback,
    Completed,
}

data class VoiceQuizState(
    val isActive: Boolean = false,
    val phase: VoiceQuizPhase = VoiceQuizPhase.Idle,
    val questionIndex: Int = 0,
    val questionCount: Int = 0,
    val stimulusRu: String = "",
    val promptRu: String = "",
    val lastSpoken: String = "",
    val statusMessage: String = "Нажмите Next на гарнитуре, чтобы начать.",
    val totalGuessedWords: Int = 0,
    val remainingWords: Int = 0,
    val estimatedLevel: String = "A1",
) {
    val questionNumber: Int get() = questionIndex + 1
}
