"""Original A1 course in the same beginner themes as Bridge to English.

This is not the commercial course. Wording is written for this app.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "Bridge to English" / "bridge_style_a1.json"

WORDS = [
    ("hello", "здравствуйте"),
    ("goodbye", "до свидания"),
    ("please", "пожалуйста"),
    ("thanks", "спасибо"),
    ("sorry", "извините"),
    ("yes", "да"),
    ("no", "нет"),
    ("name", "имя"),
    ("morning", "утро"),
    ("evening", "вечер"),
    ("man", "мужчина"),
    ("woman", "женщина"),
    ("child", "ребёнок"),
    ("friend", "друг"),
    ("family", "семья"),
    ("mother", "мама"),
    ("father", "папа"),
    ("brother", "брат"),
    ("sister", "сестра"),
    ("husband", "муж"),
    ("wife", "жена"),
    ("son", "сын"),
    ("daughter", "дочь"),
    ("house", "дом"),
    ("room", "комната"),
    ("kitchen", "кухня"),
    ("door", "дверь"),
    ("window", "окно"),
    ("table", "стол"),
    ("chair", "стул"),
    ("bed", "кровать"),
    ("key", "ключ"),
    ("water", "вода"),
    ("bread", "хлеб"),
    ("milk", "молоко"),
    ("tea", "чай"),
    ("coffee", "кофе"),
    ("apple", "яблоко"),
    ("soup", "суп"),
    ("meat", "мясо"),
    ("fish", "рыба"),
    ("one", "один"),
    ("two", "два"),
    ("three", "три"),
    ("ten", "десять"),
    ("hour", "час"),
    ("minute", "минута"),
    ("today", "сегодня"),
    ("tomorrow", "завтра"),
    ("week", "неделя"),
    ("day", "день"),
    ("night", "ночь"),
    ("city", "город"),
    ("street", "улица"),
    ("shop", "магазин"),
    ("bus", "автобус"),
    ("car", "машина"),
    ("ticket", "билет"),
    ("map", "карта"),
    ("station", "станция"),
    ("work", "работа"),
    ("school", "школа"),
    ("teacher", "учитель"),
    ("student", "ученик"),
    ("book", "книга"),
    ("pen", "ручка"),
    ("breakfast", "завтрак"),
    ("lunch", "обед"),
    ("dinner", "ужин"),
    ("sun", "солнце"),
    ("rain", "дождь"),
    ("snow", "снег"),
    ("wind", "ветер"),
    ("weather", "погода"),
    ("good", "хороший"),
    ("bad", "плохой"),
    ("big", "большой"),
    ("small", "маленький"),
    ("new", "новый"),
    ("old", "старый"),
    ("hot", "горячий"),
    ("cold", "холодный"),
    ("be", "быть"),
    ("have", "иметь"),
    ("go", "идти"),
    ("come", "приходить"),
    ("see", "видеть"),
    ("want", "хотеть"),
    ("need", "нуждаться"),
    ("like", "нравиться"),
    ("live", "жить"),
    ("speak", "говорить"),
    ("eat", "есть"),
    ("drink", "пить"),
    ("buy", "покупать"),
    ("open", "открывать"),
    ("close", "закрывать"),
]

PHRASES = [
    ("good morning", "доброе утро", ["morning", "good"]),
    ("good evening", "добрый вечер", ["evening", "good"]),
    ("my name", "моё имя", ["name"]),
    ("a good friend", "хороший друг", ["good", "friend"]),
    ("my family", "моя семья", ["family"]),
    ("my mother", "моя мама", ["mother"]),
    ("my father", "мой папа", ["father"]),
    ("a big house", "большой дом", ["big", "house"]),
    ("a small room", "маленькая комната", ["small", "room"]),
    ("the front door", "входная дверь", ["door"]),
    ("a cup of tea", "чашка чая", ["tea"]),
    ("a cup of coffee", "чашка кофе", ["coffee"]),
    ("a glass of water", "стакан воды", ["water"]),
    ("a glass of milk", "стакан молока", ["milk"]),
    ("a piece of bread", "кусок хлеба", ["bread"]),
    ("hot soup", "горячий суп", ["hot", "soup"]),
    ("ten minutes", "десять минут", ["ten", "minute"]),
    ("one hour", "один час", ["one", "hour"]),
    ("today and tomorrow", "сегодня и завтра", ["today", "tomorrow"]),
    ("this week", "на этой неделе", ["week"]),
    ("in the city", "в городе", ["city"]),
    ("on the street", "на улице", ["street"]),
    ("the bus stop", "остановка автобуса", ["bus", "station"]),
    ("a bus ticket", "билет на автобус", ["bus", "ticket"]),
    ("a city map", "карта города", ["city", "map"]),
    ("at school", "в школе", ["school"]),
    ("my teacher", "мой учитель", ["teacher"]),
    ("a new book", "новая книга", ["new", "book"]),
    ("at work", "на работе", ["work"]),
    ("breakfast time", "время завтрака", ["breakfast"]),
    ("good weather", "хорошая погода", ["good", "weather"]),
    ("heavy rain", "сильный дождь", ["rain"]),
    ("cold wind", "холодный ветер", ["cold", "wind"]),
    ("white snow", "белый снег", ["snow"]),
    ("the hot sun", "жаркое солнце", ["hot", "sun"]),
    ("come in", "входите", ["come"]),
    ("go home", "идти домой", ["go", "house"]),
    ("speak English", "говорить по-английски", ["speak"]),
    ("open the door", "открыть дверь", ["open", "door"]),
    ("close the window", "закрыть окно", ["close", "window"]),
]

SENTENCES = [
    ("Hello. My name is Anna.", "Здравствуйте. Меня зовут Анна.", ["hello", "name"]),
    ("Good morning. How are you?", "Доброе утро. Как вы?", ["morning", "good"]),
    ("Thank you very much.", "Большое спасибо.", ["thanks"]),
    ("I am sorry. Please come in.", "Извините. Пожалуйста, входите.", ["sorry", "please", "come"]),
    ("This is my friend.", "Это мой друг.", ["friend"]),
    ("I have a brother and a sister.", "У меня есть брат и сестра.", ["have", "brother", "sister"]),
    ("My mother and father live here.", "Мои мама и папа живут здесь.", ["mother", "father", "live"]),
    ("We have a small house.", "У нас маленький дом.", ["have", "small", "house"]),
    ("The key is on the table.", "Ключ на столе.", ["key", "table"]),
    ("Please open the door.", "Пожалуйста, откройте дверь.", ["please", "open", "door"]),
    ("I want a cup of tea.", "Я хочу чашку чая.", ["want", "tea"]),
    ("She likes coffee in the morning.", "Она любит кофе утром.", ["like", "coffee", "morning"]),
    ("We eat soup for lunch.", "Мы едим суп на обед.", ["eat", "soup", "lunch"]),
    ("He drinks milk at breakfast.", "Он пьёт молоко за завтраком.", ["drink", "milk", "breakfast"]),
    ("I need bread from the shop.", "Мне нужен хлеб из магазина.", ["need", "bread", "shop"]),
    ("Today is a good day.", "Сегодня хороший день.", ["today", "good", "day"]),
    ("See you tomorrow evening.", "Увидимся завтра вечером.", ["see", "tomorrow", "evening"]),
    ("The bus comes in ten minutes.", "Автобус придёт через десять минут.", ["bus", "come", "ten", "minute"]),
    ("I go to work by car.", "Я езжу на работу на машине.", ["go", "work", "car"]),
    ("Where is the station?", "Где станция?", ["station"]),
    ("Can I buy a ticket here?", "Можно купить билет здесь?", ["buy", "ticket"]),
    ("The teacher has a new book.", "У учителя новая книга.", ["teacher", "have", "new", "book"]),
    ("The student writes with a pen.", "Ученик пишет ручкой.", ["student", "pen"]),
    ("I live near the school.", "Я живу рядом со школой.", ["live", "school"]),
    ("It is night, and I go to bed.", "Ночь, и я иду спать.", ["night", "go", "bed"]),
    ("We have dinner at seven.", "Мы ужинаем в семь.", ["have", "dinner"]),
    ("The weather is cold today.", "Сегодня погода холодная.", ["weather", "cold", "today"]),
    ("The sun is hot, but the wind is cold.", "Солнце жаркое, но ветер холодный.", ["sun", "hot", "wind", "cold"]),
    ("I do not like rain.", "Мне не нравится дождь.", ["like", "rain"]),
    ("Snow is on the street.", "На улице снег.", ["snow", "street"]),
    ("Yes, this shop is open.", "Да, этот магазин открыт.", ["yes", "shop", "open"]),
    ("No, the window is closed.", "Нет, окно закрыто.", ["no", "window", "close"]),
    ("I want to speak English every day.", "Я хочу говорить по-английски каждый день.", ["want", "speak", "day"]),
    ("Goodbye. See you next week.", "До свидания. Увидимся на следующей неделе.", ["goodbye", "see", "week"]),
]


def main() -> None:
    words = [{"en": en, "ru": ru} for en, ru in WORDS]
    keys = [w["en"].casefold() for w in words]
    if len(keys) != len(set(keys)):
        raise SystemExit("duplicate word")
    known = set(keys)

    def cards(rows):
        out = []
        for en, ru, uses in rows:
            missing = [u for u in uses if u.casefold() not in known]
            if missing:
                raise SystemExit(f"{en!r} uses unknown {missing}")
            out.append({"en": en, "ru": ru, "uses": uses})
        return out

    payload = {
        "id": "bridge_style_a1",
        "titleRu": "Базовый курс A1",
        "words": words,
        "phrases": cards(PHRASES),
        "sentences": cards(SENTENCES),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"words={len(words)} phrases={len(PHRASES)} sentences={len(SENTENCES)} -> {OUT}")


if __name__ == "__main__":
    main()
