"""CLB 5 / CELPIP prep lessons for AET, aligned with Study/CLB5-AET.txt.

Original wording for this app. Not copied from official CLB or CELPIP tests.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "topics" / "CLB"


def card(en: str, ru: str, uses: list[str] | None = None) -> dict:
    item = {"en": en, "ru": ru}
    if uses is not None:
        item["uses"] = uses
    return item


def validate(words: list[tuple[str, str]], phrases, sentences) -> tuple[list, list, list]:
    word_rows = [{"en": en, "ru": ru} for en, ru in words]
    keys = [w["en"].casefold() for w in word_rows]
    if len(keys) != len(set(keys)):
        dups = sorted({k for k in keys if keys.count(k) > 1})
        raise SystemExit(f"duplicate words: {dups}")
    known = set(keys)

    def pack(rows):
        out = []
        errors = []
        for en, ru, uses in rows:
            missing = [u for u in uses if u.casefold() not in known]
            if missing:
                errors.append(f"{en!r} uses unknown {missing}")
            out.append({"en": en, "ru": ru, "uses": uses})
        if errors:
            raise SystemExit("\n".join(errors))
        return out

    return word_rows, pack(phrases), pack(sentences)


def write_topic(topic_id: str, title_ru: str, words, phrases, sentences) -> None:
    w, p, s = validate(words, phrases, sentences)
    payload = {
        "id": topic_id,
        "titleRu": title_ru,
        "words": w,
        "phrases": p,
        "sentences": s,
    }
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / f"{topic_id}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{topic_id}: words={len(w)} phrases={len(p)} sentences={len(s)} -> {path}")


# --- Week 1: Present Simple, articles, daily routines, reading correspondence ---

WEEK1_WORDS = [
    ("go", "идти, ехать"),
    ("work", "работать"),
    ("live", "жить"),
    ("like", "нравиться"),
    ("need", "нуждаться"),
    ("want", "хотеть"),
    ("catch", "успеть сесть / поймать"),
    ("finish", "закончить"),
    ("wake", "просыпаться"),
    ("start", "начинать"),
    ("early", "рано"),
    ("late", "поздно"),
    ("usually", "обычно"),
    ("always", "всегда"),
    ("never", "никогда"),
    ("often", "часто"),
    ("bus", "автобус"),
    ("office", "офис"),
    ("home", "дом"),
    ("kitchen", "кухня"),
    ("grandmother", "бабушка"),
    ("umbrella", "зонт"),
    ("hour", "час"),
    ("sandwich", "сэндвич"),
    ("grocery", "продуктовый"),
    ("store", "магазин"),
    ("email", "электронное письмо"),
    ("letter", "письмо"),
    ("article", "артикл / статья"),
    ("routine", "распорядок"),
    ("morning", "утро"),
    ("evening", "вечер"),
    ("breakfast", "завтрак"),
    ("dinner", "ужин"),
    ("neighbour", "сосед"),
    ("apartment", "квартира"),
    ("schedule", "расписание"),
    ("practice", "практика / практиковаться"),
    ("review", "повторение / повторять"),
    ("weak", "слабый"),
    ("goal", "цель"),
    ("fact", "факт"),
    ("question", "вопрос"),
    ("answer", "ответ"),
    ("explain", "объяснять"),
    ("read", "читать"),
    ("write", "писать"),
    ("speak", "говорить"),
    ("listen", "слушать"),
    ("study", "учиться"),
]

WEEK1_PHRASES = [
    ("wake up", "проснуться", ["wake"]),
    ("get up", "встать с кровати", ["wake"]),
    ("catch the bus", "успеть на автобус", ["catch", "bus"]),
    ("finish work", "закончить работу", ["finish", "work"]),
    ("go to work", "ехать на работу", ["go", "work"]),
    ("by bus", "на автобусе", ["bus"]),
    ("every morning", "каждое утро", ["morning"]),
    ("in the evening", "вечером", ["evening"]),
    ("daily routine", "ежедневный распорядок", ["routine"]),
    ("a short email", "короткое письмо", ["email"]),
    ("grocery store", "продуктовый магазин", ["grocery", "store"]),
    ("living room", "гостиная", ["home"]),
    ("Present Simple", "настоящее простое время", ["study"]),
    ("a or an", "неопределённый артикль", ["article"]),
    ("the known thing", "определённый артикль the", ["article"]),
    ("an umbrella", "зонт (перед гласным звуком)", ["umbrella", "article"]),
    ("an hour", "час (h немая)", ["hour", "article"]),
    ("a university", "университет (звук /ju/)", ["article"]),
    ("do not like", "не нравится (they/we/I)", ["like"]),
    ("does not work", "не работает (he/she/it)", ["work"]),
]

WEEK1_SENTENCES = [
    ("She goes to work by bus.", "Она ездит на работу на автобусе.", ["go", "work", "bus"]),
    ("They do not like cold weather.", "Им не нравится холодная погода.", ["like"]),
    ("Does your brother live here?", "Твой брат здесь живёт?", ["live"]),
    ("I wake up early and get up at seven.", "Я просыпаюсь рано и встаю в семь.", ["wake", "early"]),
    ("I usually catch the bus near my apartment.", "Я обычно сажусь на автобус около квартиры.", ["usually", "catch", "bus", "apartment"]),
    ("I finish work at five and cook dinner in the kitchen.", "Я заканчиваю работу в пять и готовлю ужин на кухне.", ["finish", "work", "dinner", "kitchen"]),
    ("I bought a sandwich. The sandwich was cold.", "Я купил сэндвич. Сэндвич был холодный.", ["sandwich", "article"]),
    ("Please take an umbrella. It may rain this evening.", "Возьмите зонт. Вечером может пойти дождь.", ["umbrella", "evening"]),
    ("My grandmother lives near the grocery store.", "Моя бабушка живёт рядом с продуктовым магазином.", ["grandmother", "live", "grocery", "store"]),
    ("I need to read a short email and answer one question.", "Мне нужно прочитать короткое письмо и ответить на один вопрос.", ["need", "read", "email", "answer", "question"]),
    ("Week one goal: Present Simple, articles, and daily words.", "Цель недели 1: Present Simple, артикли и бытовые слова.", ["goal", "article", "routine"]),
    ("Study twenty minutes of cards and one short lesson every day.", "Учите двадцать минут карточек и один короткий урок каждый день.", ["study", "practice"]),
    ("Open the Present Simple lesson in beginner mode first.", "Сначала откройте урок Present Simple в режиме новичка.", ["study", "explain"]),
    ("Review only the weak cards before the mini test.", "Перед мини-тестом повторите только слабые карточки.", ["review", "weak"]),
    ("Find facts in the email. Do not translate every word.", "Ищите факты в письме. Не переводите каждое слово.", ["fact", "email", "read"]),
]


# --- Week 2: Past / future, reading for information, listening, email writing ---

WEEK2_WORDS = [
    ("yesterday", "вчера"),
    ("ago", "назад"),
    ("last", "прошлый"),
    ("week", "неделя"),
    ("plan", "план"),
    ("will", "буду / будет"),
    ("going", "собирающийся"),
    ("table", "таблица"),
    ("notice", "объявление"),
    ("instruction", "инструкция"),
    ("dialogue", "диалог"),
    ("detail", "подробность"),
    ("gist", "общий смысл"),
    ("request", "просьба"),
    ("polite", "вежливый"),
    ("purpose", "цель письма"),
    ("greeting", "приветствие"),
    ("close", "завершение письма"),
    ("point", "пункт"),
    ("cover", "охватить / покрыть"),
    ("example", "пример"),
    ("error", "ошибка"),
    ("score", "результат / балл"),
    ("level", "уровень"),
    ("mode", "режим"),
    ("beginner", "новичок"),
    ("practice", "практика"),
    ("exam", "экзамен"),
    ("listen", "слушать"),
    ("read", "читать"),
    ("write", "писать"),
    ("send", "отправлять"),
    ("ask", "просить / спрашивать"),
    ("help", "помощь / помогать"),
    ("centre", "центр"),
    ("community", "община"),
    ("neighbour", "сосед"),
    ("noise", "шум"),
    ("repair", "ремонт"),
    ("appointment", "запись"),
    ("tomorrow", "завтра"),
    ("tonight", "сегодня вечером"),
    ("future", "будущее"),
    ("past", "прошлое"),
    ("event", "событие"),
    ("information", "информация"),
    ("search", "искать"),
    ("twice", "дважды"),
    ("first", "сначала / первый"),
    ("then", "затем"),
    ("study", "учиться"),
    ("goal", "цель"),
]

WEEK2_PHRASES = [
    ("last week", "на прошлой неделе", ["last", "week"]),
    ("two days ago", "два дня назад", ["ago"]),
    ("going to", "собираться (план)", ["going", "plan"]),
    ("will help", "помогу / поможет", ["will", "help"]),
    ("reading for information", "чтение ради информации", ["read", "information"]),
    ("short dialogue", "короткий диалог", ["dialogue"]),
    ("listen twice", "слушать дважды", ["listen", "twice"]),
    ("first the gist", "сначала общий смысл", ["first", "gist"]),
    ("then the details", "затем подробности", ["then", "detail"]),
    ("writing an email", "написать письмо", ["write"]),
    ("polite close", "вежливое завершение", ["polite", "close"]),
    ("cover three points", "охватить три пункта", ["cover", "point"]),
    ("ask a neighbour", "попросить соседа", ["ask", "neighbour"]),
    ("community centre", "общественный центр", ["community", "centre"]),
    ("book an appointment", "записаться на приём", ["appointment"]),
    ("practice mode", "режим практики", ["practice", "mode"]),
    ("beginner mode", "режим новичка", ["beginner", "mode"]),
    ("exam mode", "экзаменационный режим", ["exam", "mode"]),
    ("at least sixty percent", "не меньше шестидесяти процентов", ["score", "level"]),
    ("past simple", "простое прошедшее", ["past"]),
]

WEEK2_SENTENCES = [
    ("Yesterday I finished work late and went home by bus.", "Вчера я поздно закончил работу и поехал домой на автобусе.", ["yesterday", "past"]),
    ("Last week I visited the community centre.", "На прошлой неделе я был в общественном центре.", ["last", "week", "community", "centre"]),
    ("Two days ago my neighbour asked for a repair.", "Два дня назад сосед попросил о ремонте.", ["ago", "neighbour", "ask", "repair"]),
    ("I am going to study for one hour tonight.", "Я собираюсь заниматься час сегодня вечером.", ["going", "study", "tonight"]),
    ("If it rains tomorrow, we will stay home.", "Если завтра будет дождь, мы останемся дома.", ["tomorrow", "will"]),
    ("Read the notice and find only the needed information.", "Прочитайте объявление и найдите только нужную информацию.", ["read", "notice", "information", "search"]),
    ("Look at the table first, then answer the question.", "Сначала посмотрите на таблицу, затем ответьте на вопрос.", ["table", "first", "then"]),
    ("Listen to the short dialogue twice: first for gist, then for details.", "Слушайте короткий диалог дважды: сначала общий смысл, затем детали.", ["listen", "dialogue", "twice", "gist", "detail"]),
    ("Write a polite email to your neighbour about the noise.", "Напишите вежливое письмо соседу о шуме.", ["write", "polite", "neighbour", "noise"]),
    ("Start with a greeting, state the purpose, and cover three points.", "Начните с приветствия, укажите цель и охватите три пункта.", ["greeting", "purpose", "cover", "point"]),
    ("End with a polite close and send the email.", "Завершите вежливо и отправьте письмо.", ["polite", "close", "send"]),
    ("Week two goal: past and future forms, reading for facts, and email writing.", "Цель недели 2: прошедшее и будущее, чтение фактов и письмо.", ["goal", "past", "future", "write"]),
    ("In practice mode try for at least sixty percent at CLB 5 level.", "В режиме практики стремитесь к ≥60% на уровне CLB 5.", ["practice", "mode", "score", "level"]),
    ("Review your errors, then use beginner mode only for weak questions.", "Разберите ошибки, затем режим новичка только для слабых вопросов.", ["error", "beginner", "mode"]),
    ("I will book an appointment at the community centre tomorrow.", "Завтра я запишусь в общественный центр.", ["will", "appointment", "community", "centre", "tomorrow"]),
]


# --- Week 3: Speaking S1–S2, Writing W2, listening news, reading viewpoints ---

WEEK3_WORDS = [
    ("advice", "совет"),
    ("advise", "советовать"),
    ("reason", "причина"),
    ("example", "пример"),
    ("experience", "опыт"),
    ("sequence", "последовательность"),
    ("first", "сначала"),
    ("then", "затем"),
    ("after", "после"),
    ("survey", "опрос"),
    ("choose", "выбирать"),
    ("option", "вариант"),
    ("side", "сторона"),
    ("agree", "соглашаться"),
    ("disagree", "не соглашаться"),
    ("viewpoint", "точка зрения"),
    ("linker", "связка"),
    ("although", "хотя"),
    ("however", "однако"),
    ("therefore", "поэтому"),
    ("news", "новости"),
    ("name", "имя"),
    ("number", "число"),
    ("time", "время"),
    ("note", "заметка / записывать"),
    ("clear", "ясный"),
    ("direct", "прямой"),
    ("ending", "окончание"),
    ("second", "секунда / второй"),
    ("minute", "минута"),
    ("story", "история"),
    ("personal", "личный"),
    ("support", "поддержка / поддерживать"),
    ("opinion", "мнение"),
    ("compare", "сравнивать"),
    ("statistics", "статистика"),
    ("mock", "пробный"),
    ("exam", "экзамен"),
    ("hint", "подсказка"),
    ("sleep", "сон / спать"),
    ("habit", "привычка"),
    ("target", "цель"),
    ("listening", "аудирование"),
    ("reading", "чтение"),
    ("writing", "письмо"),
    ("speaking", "говорение"),
    ("submit", "сдать / отправить на проверку"),
    ("improve", "улучшать"),
    ("plan", "план"),
    ("week", "неделя"),
]

WEEK3_PHRASES = [
    ("giving advice", "дать совет", ["advice"]),
    ("two reasons", "две причины", ["reason"]),
    ("short personal example", "короткий личный пример", ["personal", "example"]),
    ("personal experience", "личный опыт", ["personal", "experience"]),
    ("after that", "после этого", ["after", "then"]),
    ("choose a side", "выбрать сторону", ["choose", "side"]),
    ("survey question", "вопрос опроса", ["survey"]),
    ("why not the other option", "почему не другой вариант", ["option"]),
    ("even though", "хотя / даже если", ["although"]),
    ("linker words", "слова-связки", ["linker"]),
    ("names and numbers", "имена и числа", ["name", "number"]),
    ("exam mode mock", "пробный блок в режиме экзамена", ["exam", "mock"]),
    ("without hints", "без подсказок", ["hint"]),
    ("clear ending", "ясное завершение", ["clear", "ending"]),
    ("direct answer first", "сначала прямой ответ", ["direct", "clear"]),
    ("sixty to ninety seconds", "шестьдесят–девяносто секунд", ["second", "minute"]),
    ("hand in", "сдать работу", ["submit"]),
    ("daily minimum", "ежедневный минимум", ["habit"]),
    ("before test day", "перед днём теста", ["exam"]),
    ("weak tags only", "только слабые темы", ["plan"]),
]

WEEK3_SENTENCES = [
    ("I advise you to practice speaking for sixty to ninety seconds.", "Советую практиковать говорение шестьдесят–девяносто секунд.", ["advise", "speaking", "second", "minute"]),
    ("Give advice first, then two reasons, then a short example.", "Сначала дайте совет, затем две причины, затем короткий пример.", ["advice", "reason", "example"]),
    ("First I arrived late. Then I called. After that I apologized.", "Сначала я опоздал. Затем позвонил. После этого извинился.", ["first", "then", "after", "experience"]),
    ("In the survey I choose option A for two clear reasons.", "В опросе я выбираю вариант A по двум ясным причинам.", ["survey", "choose", "option", "reason"]),
    ("I support side B. However, side A is cheaper.", "Я за сторону B. Однако сторона A дешевле.", ["support", "side", "however"]),
    ("Although I disagree with this viewpoint, I understand the reason.", "Хотя я не согласен с этой точкой зрения, я понимаю причину.", ["although", "disagree", "viewpoint", "reason"]),
    ("Therefore, I will submit my answer before the time ends.", "Поэтому я сдам ответ до конца времени.", ["therefore", "submit", "time"]),
    ("While listening to the news, note names, numbers, and time.", "Слушая новости, записывайте имена, числа и время.", ["listening", "news", "note", "name", "number", "time"]),
    ("In reading viewpoints, find who agrees and who disagrees.", "В чтении мнений найдите, кто согласен, а кто нет.", ["reading", "viewpoint", "agree", "disagree"]),
    ("A clear speaking answer starts with a direct reply and ends clearly.", "Ясный устный ответ начинается с прямого ответа и ясно заканчивается.", ["speaking", "clear", "direct", "ending"]),
    ("Week three goal: speaking advice and experience, plus survey writing.", "Цель недели 3: говорение (совет и опыт) и ответ на опрос.", ["speaking", "advice", "experience", "writing", "survey"]),
    ("Take one full mock in exam mode without hints.", "Пройдите один полный пробный блок в режиме экзамена без подсказок.", ["mock", "exam", "hint"]),
    ("Compare your statistics across beginner, practice, and exam modes.", "Сравните статистику по режимам новичок, практика и экзамен.", ["compare", "statistics"]),
    ("Daily minimum: twenty minutes of cards plus one lesson or writing task.", "Ежедневный минимум: двадцать минут карточек плюс урок или письмо.", ["habit", "writing"]),
    ("Before test day, sleep well and review only weak tags.", "Перед днём теста хорошо выспитесь и повторите только слабые темы.", ["sleep", "exam", "plan"]),
    ("My target for listening is short everyday dialogues and simple information.", "Моя цель по аудированию — короткие бытовые диалоги и простая информация.", ["target", "listening"]),
    ("My target for writing is a clear email or survey with three covered points.", "Цель по письму — ясное письмо или опрос с тремя пунктами.", ["target", "writing", "survey"]),
    ("I lived here since 2020 is wrong. Say: I have lived here since 2020.", "«I lived here since 2020» неверно. Нужно: I have lived here since 2020.", ["improve", "plan"]),
]


# --- Core grammar & checklist file (from Anki bottom cards + checklists) ---

# Unique to checklists (must not overlap week1–3 English lemmas).
CORE_WORDS = [
    ("since", "с (момента времени)"),
    ("for", "в течение"),
    ("rain", "дождь / идёт дождь"),
    ("stay", "оставаться"),
    ("though", "хотя"),
    ("hand", "передать / сдать"),
    ("solve", "решать"),
    ("problem", "проблема"),
    ("daily", "ежедневный"),
    ("conversation", "разговор"),
    ("correspondence", "переписка"),
    ("choice", "выбор"),
    ("window", "окно"),
    ("translate", "переводить"),
    ("word", "слово"),
    ("tag", "метка / тег"),
    ("checklist", "чек-лист"),
    ("criterion", "критерий"),
    ("rubric", "шкала оценки"),
    ("threshold", "порог"),
    ("fluency", "беглость"),
    ("accuracy", "точность"),
    ("coherence", "связность"),
    ("paragraph", "абзац"),
    ("outline", "план текста"),
    ("draft", "черновик"),
    ("revise", "пересмотреть / править"),
    ("proofread", "вычитать"),
    ("timing", "распределение времени"),
    ("stopwatch", "секундомер"),
    ("prompt", "задание / формулировка"),
    ("response", "ответ (развёрнутый)"),
    ("structure", "структура"),
    ("transition", "переход"),
    ("summarize", "кратко изложить"),
    ("paraphrase", "перефразировать"),
    ("skim", "пробежать глазами"),
    ("scan", "искать конкретное"),
    ("inference", "вывод"),
    ("distractor", "отвлекающий вариант"),
    ("bandwidth", "запас внимания"),
    ("warmup", "разминка"),
    ("cooldown", "завершение занятия"),
    ("streak", "серия дней подряд"),
    ("milestone", "контрольная точка"),
    ("benchmark", "ориентир"),
    ("feedback", "обратная связь"),
    ("selfcheck", "самопроверка"),
    ("template", "шаблон"),
    ("phrasebank", "запас фраз"),
]

CORE_PHRASES = [
    ("since 2020", "с 2020 года", ["since"]),
    ("for three years", "в течение трёх лет", ["for"]),
    ("even though", "хотя / даже если", ["though"]),
    ("hand in the draft", "сдать черновик", ["hand", "draft"]),
    ("solve the problem", "решить проблему", ["solve", "problem"]),
    ("daily conversation", "ежедневный разговор", ["daily", "conversation"]),
    ("reading correspondence", "чтение переписки", ["correspondence"]),
    ("survey checklist", "чек-лист опроса", ["checklist", "choice"]),
    ("email checklist", "чек-лист письма", ["checklist"]),
    ("speaking checklist", "чек-лист говорения", ["checklist", "fluency"]),
    ("timing with a stopwatch", "контроль времени по секундомеру", ["timing", "stopwatch"]),
    ("revise the outline", "пересмотреть план", ["revise", "outline"]),
    ("proofread the paragraph", "вычитать абзац", ["proofread", "paragraph"]),
    ("skim then scan", "сначала пробежать, потом искать", ["skim", "scan"]),
    ("paraphrase the prompt", "перефразировать задание", ["paraphrase", "prompt"]),
    ("summarize the response", "кратко изложить ответ", ["summarize", "response"]),
    ("use a transition", "использовать переход", ["transition"]),
    ("check the rubric", "свериться со шкалой", ["rubric", "criterion"]),
    ("reach the threshold", "достичь порога", ["threshold"]),
    ("build a phrasebank", "собрать запас фраз", ["phrasebank", "template"]),
]

CORE_SENTENCES = [
    ("I have lived here since 2020.", "Я живу здесь с 2020 года.", ["since"]),
    ("I have studied English for three years.", "Я изучаю английский три года.", ["for"]),
    ("If it rains tomorrow, we will stay home.", "Если завтра будет дождь, мы останемся дома.", ["rain", "stay"]),
    ("Though and even though have a similar meaning.", "Though и even though близки по смыслу.", ["though"]),
    ("Hand in the draft after you proofread each paragraph.", "Сдайте черновик после вычитки каждого абзаца.", ["hand", "draft", "proofread", "paragraph"]),
    ("Use the checklist: outline, structure, transition, and clear response.", "Используйте чек-лист: план, структура, переход и ясный ответ.", ["checklist", "outline", "structure", "transition", "response"]),
    ("Skim the text first, then scan for the exact word.", "Сначала пробегите текст глазами, затем ищите нужное слово.", ["skim", "scan", "word"]),
    ("Paraphrase the prompt before you write the first paragraph.", "Перефразируйте задание, прежде чем писать первый абзац.", ["paraphrase", "prompt", "paragraph"]),
    ("Summarize your response in one short sentence at the end.", "В конце кратко изложите ответ одним предложением.", ["summarize", "response"]),
    ("Check the rubric and each criterion before you submit the draft.", "Сверьтесь со шкалой и каждым критерием, прежде чем сдать черновик.", ["rubric", "criterion", "draft"]),
    ("Fluency and accuracy both matter; coherence keeps the story clear.", "Важны и беглость, и точность; связность делает рассказ ясным.", ["fluency", "accuracy", "coherence"]),
    ("Time the warmup with a stopwatch, then start the real prompt.", "Засеките разминку по секундомеру, затем начните настоящее задание.", ["warmup", "stopwatch", "timing", "prompt"]),
    ("A daily conversation streak is a useful milestone.", "Серия ежедневных разговоров — полезная контрольная точка.", ["daily", "conversation", "streak", "milestone"]),
    ("Use selfcheck and feedback to raise your benchmark.", "Используйте самопроверку и обратную связь, чтобы поднять ориентир.", ["selfcheck", "feedback", "benchmark"]),
    ("Keep a phrasebank template for correspondence and problem solving.", "Держите шаблон запаса фраз для переписки и решения проблем.", ["phrasebank", "template", "correspondence", "problem", "solve"]),
    ("Ignore the distractor and look for the real inference.", "Не отвлекайтесь на ложный вариант и ищите настоящий вывод.", ["distractor", "inference"]),
    ("After the cooldown, tag only the weak spots for review.", "После завершения занятия пометьте только слабые места.", ["cooldown", "tag"]),
]


def dedupe_across_folder(
    topics: list[tuple[str, str, list, list, list]],
) -> list[tuple[str, str, list, list, list]]:
    """Keep each English word/phrase/sentence in only one CLB file (first wins)."""
    used_words: set[str] = set()
    used_phrases: set[str] = set()
    used_sentences: set[str] = set()
    result: list[tuple[str, str, list, list, list]] = []

    for topic_id, title, words, phrases, sentences in topics:
        kept_words: list[tuple[str, str]] = []
        for en, ru in words:
            key = en.casefold()
            if key in used_words:
                continue
            used_words.add(key)
            kept_words.append((en, ru))
        word_keys = {en.casefold(): en for en, _ in kept_words}

        kept_phrases: list[tuple[str, str, list[str]]] = []
        for en, ru, uses in phrases:
            key = en.casefold()
            if key in used_phrases:
                continue
            local = [word_keys[u.casefold()] for u in uses if u.casefold() in word_keys]
            if not local and word_keys:
                local = [next(iter(word_keys.values()))]
            if not local:
                continue
            used_phrases.add(key)
            kept_phrases.append((en, ru, local))

        kept_sentences: list[tuple[str, str, list[str]]] = []
        for en, ru, uses in sentences:
            key = en.casefold()
            if key in used_sentences:
                continue
            local = [word_keys[u.casefold()] for u in uses if u.casefold() in word_keys]
            if not local and word_keys:
                local = [next(iter(word_keys.values()))]
            if not local:
                continue
            used_sentences.add(key)
            kept_sentences.append((en, ru, local))

        if not kept_words:
            raise SystemExit(f"{topic_id}: no words left after dedupe")
        result.append((topic_id, title, kept_words, kept_phrases, kept_sentences))

    return result


def main() -> None:
    # Known words from XSHOOT are stripped at study time on the phone (TopicRepository),
    # not here — phrases/sentences may still reference those lemmas via uses.
    topics = dedupe_across_folder(
        [
            ("clb5_week1", "CLB 5 · неделя 1: основа", WEEK1_WORDS, WEEK1_PHRASES, WEEK1_SENTENCES),
            ("clb5_week2", "CLB 5 · неделя 2: навыки", WEEK2_WORDS, WEEK2_PHRASES, WEEK2_SENTENCES),
            ("clb5_week3", "CLB 5 · неделя 3: экзамен", WEEK3_WORDS, WEEK3_PHRASES, WEEK3_SENTENCES),
            ("clb5_checklists", "CLB 5 · чек-листы и цели", CORE_WORDS, CORE_PHRASES, CORE_SENTENCES),
        ]
    )
    for topic_id, title, words, phrases, sentences in topics:
        write_topic(topic_id, title, words, phrases, sentences)

    # Sanity: no shared English across the folder.
    root = OUT_DIR
    word_owners: dict[str, str] = {}
    phrase_owners: dict[str, str] = {}
    for path in sorted(root.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        for w in data["words"]:
            key = w["en"].casefold()
            if key in word_owners:
                raise SystemExit(f"shared word {w['en']!r}: {word_owners[key]} and {path.stem}")
            word_owners[key] = path.stem
        for p in data["phrases"]:
            key = p["en"].casefold()
            if key in phrase_owners:
                raise SystemExit(f"shared phrase {p['en']!r}: {phrase_owners[key]} and {path.stem}")
            phrase_owners[key] = path.stem
    print(f"folder OK: {len(word_owners)} unique words, {len(phrase_owners)} unique phrases")


if __name__ == "__main__":
    main()
