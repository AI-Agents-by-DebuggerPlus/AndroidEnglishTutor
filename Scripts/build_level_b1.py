"""Original B1 course: reasons, experience, work, travel, advice.

Wording is written for this app.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "B1" / "level_b1.json"

WORDS = [
    ("already", "уже"),
    ("yet", "ещё"),
    ("ever", "когда-либо"),
    ("since", "с тех пор"),
    ("recently", "недавно"),
    ("although", "хотя"),
    ("however", "однако"),
    ("instead", "вместо этого"),
    ("actually", "на самом деле"),
    ("especially", "особенно"),
    ("probably", "вероятно"),
    ("perhaps", "возможно"),
    ("experience", "опыт"),
    ("skill", "навык"),
    ("goal", "цель"),
    ("project", "проект"),
    ("deadline", "срок"),
    ("responsibility", "обязанность"),
    ("interview", "собеседование"),
    ("career", "карьера"),
    ("abroad", "за границей"),
    ("foreign", "иностранный"),
    ("fluent", "свободно владеющий"),
    ("accent", "акцент"),
    ("culture", "культура"),
    ("custom", "обычай"),
    ("advice", "совет"),
    ("suggest", "предлагать"),
    ("recommend", "рекомендовать"),
    ("warn", "предупреждать"),
    ("promise", "обещать"),
    ("refuse", "отказываться"),
    ("manage", "справляться"),
    ("improve", "улучшать"),
    ("develop", "развивать"),
    ("explain", "объяснять"),
    ("discuss", "обсуждать"),
    ("argue", "спорить"),
    ("agree", "соглашаться"),
    ("decide", "решать"),
    ("environment", "окружающая среда"),
    ("pollution", "загрязнение"),
    ("recycle", "перерабатывать"),
    ("traffic", "пробки"),
    ("crowded", "переполненный"),
    ("convenient", "удобный"),
    ("neighborhood", "район"),
    ("rent", "аренда"),
    ("exercise", "упражнение"),
    ("diet", "диета"),
    ("stress", "стресс"),
    ("habit", "привычка"),
    ("proud", "гордый"),
    ("disappointed", "разочарованный"),
    ("nervous", "нервный"),
    ("confident", "уверенный"),
    ("grateful", "благодарный"),
    ("jealous", "завидующий"),
    ("unless", "если не"),
    ("until", "до тех пор"),
    ("while", "пока"),
    ("during", "во время"),
    ("news", "новости"),
    ("article", "статья"),
    ("opinion", "мнение"),
    ("reason", "причина"),
    ("result", "результат"),
    ("problem", "проблема"),
    ("solution", "решение"),
    ("advantage", "преимущество"),
    ("disadvantage", "недостаток"),
    ("opportunity", "возможность"),
    ("choice", "выбор"),
    ("risk", "риск"),
    ("success", "успех"),
    ("failure", "неудача"),
    ("progress", "прогресс"),
    ("effort", "усилие"),
    ("support", "поддержка"),
    ("trust", "доверие"),
    ("respect", "уважение"),
    ("invite", "приглашать"),
    ("attend", "посещать"),
    ("cancel", "отменять"),
    ("postpone", "откладывать"),
    ("prepare", "готовиться"),
    ("achieve", "достигать"),
    ("avoid", "избегать"),
    ("expect", "ожидать"),
    ("realize", "осознавать"),
    ("notice", "замечать"),
    ("compare", "сравнивать"),
    ("prefer", "предпочитать"),
    ("depend", "зависеть"),
    ("belong", "принадлежать"),
    ("include", "включать"),
    ("offer", "предлагать"),
    ("accept", "принимать"),
    ("borrow", "брать взаймы"),
    ("lend", "давать взаймы"),
    ("apologize", "извиняться"),
    ("complain", "жаловаться"),
]

PHRASES = [
    ("work experience", "опыт работы", ["experience"]),
    ("a useful skill", "полезный навык", ["skill"]),
    ("a clear goal", "ясная цель", ["goal"]),
    ("a tight deadline", "сжатый срок", ["deadline"]),
    ("a job interview", "собеседование", ["interview"]),
    ("study abroad", "учиться за границей", ["abroad"]),
    ("a foreign language", "иностранный язык", ["foreign"]),
    ("a strong accent", "сильный акцент", ["accent"]),
    ("local custom", "местный обычай", ["custom"]),
    ("good advice", "хороший совет", ["advice"]),
    ("air pollution", "загрязнение воздуха", ["pollution"]),
    ("heavy traffic", "сильные пробки", ["traffic"]),
    ("a crowded bus", "переполненный автобус", ["crowded"]),
    ("a convenient place", "удобное место", ["convenient"]),
    ("monthly rent", "месячная аренда", ["rent"]),
    ("daily exercise", "ежедневная зарядка", ["exercise"]),
    ("a healthy diet", "здоровое питание", ["diet"]),
    ("work stress", "рабочий стресс", ["stress"]),
    ("a bad habit", "плохая привычка", ["habit"]),
    ("feel nervous", "нервничать", ["nervous"]),
    ("feel confident", "чувствовать уверенность", ["confident"]),
    ("the main reason", "главная причина", ["reason"]),
    ("a possible solution", "возможное решение", ["solution"]),
    ("a big advantage", "большое преимущество", ["advantage"]),
    ("a real opportunity", "настоящая возможность", ["opportunity"]),
    ("make progress", "делать успехи", ["progress"]),
    ("make an effort", "приложить усилие", ["effort"]),
    ("public support", "общественная поддержка", ["support"]),
    ("mutual respect", "взаимное уважение", ["respect"]),
    ("cancel a meeting", "отменить встречу", ["cancel"]),
    ("postpone a trip", "отложить поездку", ["postpone"]),
    ("prepare for an interview", "готовиться к собеседованию", ["prepare", "interview"]),
    ("avoid the risk", "избежать риска", ["avoid", "risk"]),
    ("accept an offer", "принять предложение", ["accept", "offer"]),
    ("borrow money", "взять деньги взаймы", ["borrow"]),
    ("apologize for the delay", "извиниться за задержку", ["apologize"]),
    ("complain about the noise", "пожаловаться на шум", ["complain"]),
    ("depend on the weather", "зависеть от погоды", ["depend"]),
    ("belong to the team", "быть частью команды", ["belong"]),
    ("compare two options", "сравнить два варианта", ["compare", "choice"]),
]

SENTENCES = [
    ("I have already finished the project, but I have not sent it yet.", "Я уже закончил проект, но ещё не отправил его.", ["already", "project", "yet"]),
    ("Have you ever worked abroad?", "Ты когда-нибудь работал за границей?", ["ever", "abroad"]),
    ("I have lived here since last year.", "Я живу здесь с прошлого года.", ["since"]),
    ("Although the rent is high, the neighborhood is convenient.", "Хотя аренда высокая, район удобный.", ["although", "rent", "neighborhood", "convenient"]),
    ("The traffic was bad. However, I arrived on time.", "Пробки были сильные. Однако я приехал вовремя.", ["traffic", "however"]),
    ("I actually prefer a quiet office.", "На самом деле я предпочитаю тихий офис.", ["actually", "prefer"]),
    ("She is especially good at explaining difficult ideas.", "Она особенно хорошо объясняет сложные мысли.", ["especially", "explain"]),
    ("We will probably postpone the meeting.", "Мы, вероятно, отложим встречу.", ["probably", "postpone"]),
    ("Perhaps you should ask for advice.", "Возможно, тебе стоит попросить совет.", ["perhaps", "advice"]),
    ("He suggested a simple solution.", "Он предложил простое решение.", ["suggest", "solution"]),
    ("I recommend this course if you want to become fluent.", "Я рекомендую этот курс, если хочешь заговорить свободно.", ["recommend", "fluent"]),
    ("They warned us about the deadline.", "Они предупредили нас о сроке.", ["warn", "deadline"]),
    ("I promise I will prepare tonight.", "Обещаю, я подготовлюсь сегодня вечером.", ["promise", "prepare"]),
    ("She refused the offer because the salary was low.", "Она отказалась от предложения, потому что зарплата была низкой.", ["refuse", "offer"]),
    ("I managed to finish it during the break.", "Я сумел закончить это во время перерыва.", ["manage", "during"]),
    ("Daily exercise helps me reduce stress.", "Ежедневная зарядка помогает мне снизить стресс.", ["exercise", "stress"]),
    ("A healthy diet is a habit I am trying to develop.", "Здоровое питание — привычка, которую я стараюсь выработать.", ["diet", "habit", "develop"]),
    ("I felt nervous before the interview, but now I feel confident.", "Я нервничал перед собеседованием, но теперь чувствую уверенность.", ["nervous", "interview", "confident"]),
    ("She was disappointed with the result.", "Она была разочарована результатом.", ["disappointed", "result"]),
    ("I am grateful for your support.", "Я благодарен за твою поддержку.", ["grateful", "support"]),
    ("Unless we leave now, we will miss the train.", "Если не выйдем сейчас, опоздаем на поезд.", ["unless"]),
    ("Wait until I call you.", "Подожди, пока я тебе позвоню.", ["until"]),
    ("While I was reading the article, I noticed a mistake.", "Пока я читал статью, я заметил ошибку.", ["while", "article", "notice"]),
    ("In my opinion, recycling is worth the effort.", "По-моему, переработка стоит усилий.", ["opinion", "recycle", "effort"]),
    ("The main advantage is the price, and the disadvantage is the noise.", "Главное преимущество — цена, а недостаток — шум.", ["advantage", "disadvantage"]),
    ("This choice depends on your goal.", "Этот выбор зависит от твоей цели.", ["choice", "depend", "goal"]),
    ("We discussed the problem and agreed on a plan.", "Мы обсудили проблему и согласовали план.", ["discuss", "problem", "agree"]),
    ("He argued with his colleague, then apologized.", "Он поспорил с коллегой, а потом извинился.", ["argue", "apologize"]),
    ("I realized I had forgotten an important detail.", "Я понял, что забыл важную деталь.", ["realize"]),
    ("She expects to achieve her goal this year.", "Она рассчитывает достичь цели в этом году.", ["expect", "achieve", "goal"]),
    ("Can I borrow your book? I will return it tomorrow.", "Можно взять твою книгу? Я верну её завтра.", ["borrow"]),
    ("I will lend it to you if you promise to be careful.", "Я дам её тебе, если пообещаешь быть аккуратным.", ["lend", "promise"]),
    ("The meeting was cancelled, so we have more time to improve the project.", "Встречу отменили, так что у нас больше времени улучшить проект.", ["cancel", "improve", "project"]),
    ("Success takes time, and failure is part of progress.", "Успех требует времени, а неудача — часть прогресса.", ["success", "failure", "progress"]),
]


def main() -> None:
    words = [{"en": en, "ru": ru} for en, ru in WORDS]
    keys = [w["en"].casefold() for w in words]
    if len(keys) != len(set(keys)):
        raise SystemExit("duplicate word")
    known = set(keys)

    def cards(rows):
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

    payload = {
        "id": "level_b1",
        "titleRu": "Уровень B1",
        "words": words,
        "phrases": cards(PHRASES),
        "sentences": cards(SENTENCES),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"words={len(words)} phrases={len(PHRASES)} sentences={len(SENTENCES)} -> {OUT}")


if __name__ == "__main__":
    main()
