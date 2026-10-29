"""Original dictations for the Bridge to English folder.

Simple file: short monologues. Complex file: longer dialogues.
The wording is written for this app and is not taken from the commercial course.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "topics" / "Bridge to English"


def write_topic(name: str, title: str, words, phrases, sentences) -> None:
    keys = [en.casefold() for en, _ in words]
    if len(keys) != len(set(keys)):
        raise SystemExit(f"{name}: duplicate word")
    known = set(keys)

    def cards(rows):
        out = []
        errors = []
        for en, ru, uses in rows:
            missing = [u for u in uses if u.casefold() not in known]
            if missing:
                errors.append(f"{en[:48]!r} uses unknown {missing}")
            out.append({"en": en, "ru": ru, "uses": uses})
        if errors:
            raise SystemExit(name + "\n" + "\n".join(errors))
        return out

    payload = {
        "id": name,
        "titleRu": title,
        "words": [{"en": en, "ru": ru} for en, ru in words],
        "phrases": cards(phrases),
        "sentences": cards(sentences),
    }
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / f"{name}.json"
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{title}: words={len(words)} phrases={len(phrases)} sentences={len(sentences)} -> {path}")


SIMPLE_WORDS = [
    ("name", "имя"),
    ("morning", "утро"),
    ("tea", "чай"),
    ("bread", "хлеб"),
    ("bus", "автобус"),
    ("work", "работа"),
    ("family", "семья"),
    ("mother", "мама"),
    ("father", "папа"),
    ("sister", "сестра"),
    ("brother", "брат"),
    ("shop", "магазин"),
    ("apple", "яблоко"),
    ("milk", "молоко"),
    ("price", "цена"),
    ("home", "дом"),
    ("room", "комната"),
    ("window", "окно"),
    ("kitchen", "кухня"),
    ("weather", "погода"),
    ("rain", "дождь"),
    ("coat", "пальто"),
    ("phone", "телефон"),
    ("friend", "друг"),
    ("evening", "вечер"),
    ("dinner", "ужин"),
    ("park", "парк"),
    ("dog", "собака"),
    ("walk", "прогулка"),
    ("weekend", "выходные"),
    ("market", "рынок"),
    ("letter", "письмо"),
    ("school", "школа"),
    ("teacher", "учитель"),
    ("book", "книга"),
    ("water", "вода"),
    ("street", "улица"),
    ("key", "ключ"),
    ("bag", "сумка"),
    ("clock", "часы"),
]

SIMPLE_PHRASES = [
    ("every morning", "каждое утро", ["morning"]),
    ("a cup of tea", "чашка чая", ["tea"]),
    ("my family", "моя семья", ["family"]),
    ("at the shop", "в магазине", ["shop"]),
    ("the bus stop", "автобусная остановка", ["bus"]),
    ("go home", "идти домой", ["home"]),
    ("heavy rain", "сильный дождь", ["rain"]),
    ("a warm coat", "тёплое пальто", ["coat"]),
    ("phone my friend", "позвонить другу", ["phone", "friend"]),
    ("cook dinner", "готовить ужин", ["dinner"]),
    ("walk the dog", "выгуливать собаку", ["walk", "dog"]),
    ("on the weekend", "на выходных", ["weekend"]),
    ("at the market", "на рынке", ["market"]),
    ("a short letter", "короткое письмо", ["letter"]),
    ("after school", "после школы", ["school"]),
]

SIMPLE_MONOLOGUES = [
    (
        "My name is Mia. Every morning I drink tea and eat bread. Then I take the bus to work. I come home at six. I like a quiet evening.",
        "Меня зовут Миа. Каждое утро я пью чай и ем хлеб. Потом я еду на автобусе на работу. Я прихожу домой в шесть. Мне нравится тихий вечер.",
        ["name", "morning", "tea", "bread", "bus", "work", "home", "evening"],
    ),
    (
        "I have a small family. My mother cooks, and my father reads. My sister is at school. My brother walks the dog. We eat dinner together.",
        "У меня небольшая семья. Мама готовит, а папа читает. Сестра в школе. Брат выгуливает собаку. Мы ужинаем вместе.",
        ["family", "mother", "father", "sister", "school", "brother", "dog", "dinner"],
    ),
    (
        "The shop on our street opens at eight. I buy milk, bread, and apples. The price is on the bag. I pay and say thank you. Then I walk home.",
        "Магазин на нашей улице открывается в восемь. Я покупаю молоко, хлеб и яблоки. Цена указана на сумке. Я плачу и говорю спасибо. Потом иду домой.",
        ["shop", "street", "milk", "bread", "apple", "price", "bag", "home"],
    ),
    (
        "I wait for the bus at the corner. The bus is sometimes late. I look at the clock and hold my bag. At work I am rarely late. The ride takes twenty minutes.",
        "Я жду автобус на углу. Автобус иногда опаздывает. Я смотрю на часы и держу сумку. На работу я редко опаздываю. Дорога занимает двадцать минут.",
        ["bus", "clock", "bag", "work"],
    ),
    (
        "Our home has a small kitchen and one bedroom. The window looks onto the street. In the morning the room is bright. I leave the key on the table. I lock the door when I go to work.",
        "У нас дома маленькая кухня и одна спальня. Окно выходит на улицу. Утром в комнате светло. Я оставляю ключ на столе. Когда ухожу на работу, я запираю дверь.",
        ["home", "kitchen", "window", "street", "morning", "room", "key", "work"],
    ),
    (
        "The weather is cold today. Rain starts after lunch. I take my coat and close the window. The street is wet. I am glad the bus comes quickly.",
        "Сегодня холодная погода. После обеда начинается дождь. Я беру пальто и закрываю окно. Улица мокрая. Я рад, что автобус приходит быстро.",
        ["weather", "rain", "coat", "window", "street", "bus"],
    ),
    (
        "In the evening I phone my friend. I tell her about my day at work. She tells me about school. We laugh. Then I say goodbye and cook dinner.",
        "Вечером я звоню подруге. Я рассказываю ей о дне на работе. Она рассказывает мне о школе. Мы смеёмся. Потом я прощаюсь и готовлю ужин.",
        ["evening", "phone", "friend", "work", "school", "dinner"],
    ),
    (
        "On the weekend I do not go to work. I walk in the park with my dog. We sit near the water. The dog runs, and I read a book. It is a good day.",
        "На выходных я не хожу на работу. Я гуляю в парке с собакой. Мы сидим у воды. Собака бегает, а я читаю книгу. Это хороший день.",
        ["weekend", "work", "walk", "park", "dog", "water", "book"],
    ),
    (
        "I like the market on Saturday. I buy apples, bread, and milk. I talk to the seller about the price. I put the food in my bag. Then I go home and make tea.",
        "Мне нравится рынок по субботам. Я покупаю яблоки, хлеб и молоко. Я говорю с продавцом о цене. Я кладу еду в сумку. Потом иду домой и завариваю чай.",
        ["market", "apple", "bread", "milk", "price", "bag", "home", "tea"],
    ),
    (
        "Today I write a short letter to my mother. I tell her about my new room. I say the street is quiet. I thank her for the book. I will post the letter after work.",
        "Сегодня я пишу короткое письмо маме. Я рассказываю ей о новой комнате. Я пишу, что улица тихая. Я благодарю её за книгу. Я отправлю письмо после работы.",
        ["letter", "mother", "room", "street", "book", "work"],
    ),
    (
        "After school the teacher gives me a book. I read two pages at home. The story is easy. My father asks me about it at dinner. I answer, and he smiles.",
        "После школы учитель даёт мне книгу. Дома я читаю две страницы. Рассказ лёгкий. За ужином папа спрашивает меня о нём. Я отвечаю, и он улыбается.",
        ["school", "teacher", "book", "home", "father", "dinner"],
    ),
    (
        "I leave home at half past seven. I check the key, the phone, and the bag. The weather is fine, so I do not take a coat. I walk to the bus. My morning is simple.",
        "Я выхожу из дома в половине восьмого. Я проверяю ключ, телефон и сумку. Погода хорошая, поэтому пальто я не беру. Я иду к автобусу. Моё утро простое.",
        ["home", "key", "phone", "bag", "weather", "coat", "bus", "morning"],
    ),
]

COMPLEX_WORDS = [
    ("heating", "отопление"),
    ("apartment", "квартира"),
    ("landlord", "арендодатель"),
    ("repair", "ремонт"),
    ("cold", "холодный"),
    ("refund", "возврат денег"),
    ("receipt", "чек"),
    ("manager", "руководитель"),
    ("customer", "клиент"),
    ("quality", "качество"),
    ("clinic", "клиника"),
    ("symptom", "симптом"),
    ("appointment", "запись на приём"),
    ("advice", "совет"),
    ("shift", "смена"),
    ("coworker", "коллега"),
    ("deadline", "крайний срок"),
    ("schedule", "расписание"),
    ("noise", "шум"),
    ("neighbour", "сосед"),
    ("policy", "правило"),
    ("sorry", "извините"),
    ("problem", "проблема"),
    ("solution", "решение"),
    ("agree", "соглашаться"),
    ("prefer", "предпочитать"),
    ("option", "вариант"),
    ("transit", "общественный транспорт"),
    ("expensive", "дорогой"),
    ("convenient", "удобный"),
    ("training", "обучение"),
    ("safety", "безопасность"),
    ("delay", "задержка"),
    ("confirm", "подтверждать"),
    ("complaint", "жалоба"),
    ("service", "услуга"),
    ("available", "доступный"),
    ("tomorrow", "завтра"),
    ("evening", "вечер"),
    ("morning", "утро"),
]

COMPLEX_PHRASES = [
    ("the heating is off", "отопление не работает", ["heating"]),
    ("ask for a repair", "попросить о ремонте", ["repair"]),
    ("keep the receipt", "сохранить чек", ["receipt"]),
    ("a poor quality", "плохое качество", ["quality"]),
    ("book an appointment", "записаться на приём", ["appointment"]),
    ("describe the symptom", "описать симптом", ["symptom"]),
    ("change the shift", "поменять смену", ["shift"]),
    ("before the deadline", "до крайнего срока", ["deadline"]),
    ("a noise complaint", "жалоба на шум", ["noise", "complaint"]),
    ("the house policy", "правило дома", ["policy"]),
    ("two options", "два варианта", ["option"]),
    ("public transit", "общественный транспорт", ["transit"]),
    ("safety training", "обучение по безопасности", ["safety", "training"]),
    ("confirm the time", "подтвердить время", ["confirm"]),
    ("a fair solution", "справедливое решение", ["solution"]),
]

COMPLEX_DIALOGUES = [
    (
        "Sam: The heating in my apartment stopped last night.\n"
        "Lee: I am the landlord. Is the whole apartment cold?\n"
        "Sam: Yes. The bedroom is very cold in the morning.\n"
        "Lee: I can send a repair tomorrow evening.\n"
        "Sam: Thank you. Please confirm the time by phone.\n"
        "Lee: I will. Sorry about the problem.",
        "Сэм: Отопление в моей квартире остановилось прошлой ночью.\n"
        "Ли: Я арендодатель. Вся квартира холодная?\n"
        "Сэм: Да. В спальне очень холодно утром.\n"
        "Ли: Я могу прислать ремонт завтра вечером.\n"
        "Сэм: Спасибо. Подтвердите, пожалуйста, время по телефону.\n"
        "Ли: Хорошо. Извините за проблему.",
        ["heating", "apartment", "landlord", "cold", "morning", "repair", "tomorrow", "evening", "confirm", "sorry", "problem"],
    ),
    (
        "Ana: I bought this coat yesterday, but the quality is poor.\n"
        "Ben: I am the manager. Do you have the receipt?\n"
        "Ana: Yes. I am a regular customer here.\n"
        "Ben: We can offer a repair or a refund.\n"
        "Ana: I prefer a refund.\n"
        "Ben: That is fine. I agree. The service desk will help you.",
        "Ана: Я купила это пальто вчера, но качество плохое.\n"
        "Бен: Я руководитель. Чек у вас есть?\n"
        "Ана: Да. Я здесь постоянный клиент.\n"
        "Бен: Мы можем предложить ремонт или возврат денег.\n"
        "Ана: Я предпочитаю возврат.\n"
        "Бен: Хорошо. Я согласен. Стойка услуг вам поможет.",
        ["quality", "manager", "receipt", "customer", "repair", "refund", "prefer", "agree", "service"],
    ),
    (
        "Dr. Kim: What is the symptom today?\n"
        "Noah: My throat hurts, and I feel cold.\n"
        "Dr. Kim: When did this start?\n"
        "Noah: Yesterday evening. I booked this appointment this morning.\n"
        "Dr. Kim: Rest today. Drink water. Come back if it is worse tomorrow morning.\n"
        "Noah: Thank you for the advice.",
        "Доктор Ким: Какой сегодня симптом?\n"
        "Ноа: Болит горло, и мне холодно.\n"
        "Доктор Ким: Когда вам стало нужно записаться?\n"
        "Ноа: Со вчерашнего вечера.\n"
        "Доктор Ким: Сегодня отдохните. Пейте воду. Приходите снова, если завтра утром станет хуже.\n"
        "Ноа: Спасибо за совет.",
        ["symptom", "cold", "appointment", "evening", "tomorrow", "morning", "advice"],
    ),
    (
        "Rita: Can we change the schedule? I cannot work the night shift.\n"
        "Omar: I am your coworker, not the manager. What is the problem?\n"
        "Rita: I have safety training tomorrow morning.\n"
        "Omar: Then take the evening shift. I can take the night.\n"
        "Rita: Are you sure?\n"
        "Omar: Yes. Tell the manager, and she will confirm it.",
        "Рита: Можем изменить расписание? Я не могу работать в ночную смену.\n"
        "Омар: Я коллега, а не руководитель. В чём проблема?\n"
        "Рита: Завтра утром у меня обучение по безопасности.\n"
        "Омар: Тогда возьми вечернюю смену. Я могу взять ночь.\n"
        "Рита: Ты уверен?\n"
        "Омар: Да. Скажи руководителю, и она это подтвердит.",
        ["schedule", "shift", "coworker", "manager", "problem", "safety", "training", "tomorrow", "morning", "evening", "confirm"],
    ),
    (
        "Lina: Do you prefer the car or public transit?\n"
        "Jon: Transit is convenient, but it is often late.\n"
        "Lina: The car is faster. The disadvantage is that parking is expensive.\n"
        "Jon: So we have two options.\n"
        "Lina: I prefer transit when the weather is bad.\n"
        "Jon: I agree. That is a good solution.",
        "Лина: Ты предпочитаешь машину или общественный транспорт?\n"
        "Джон: Транспорт удобный, но часто опаздывает.\n"
        "Лина: На машине быстрее. Недостаток в том, что парковка дорогая.\n"
        "Джон: Значит, у нас два варианта.\n"
        "Лина: Я предпочитаю транспорт, когда плохая погода.\n"
        "Джон: Согласен. Это хорошее решение.",
        ["prefer", "transit", "convenient", "expensive", "option", "agree", "solution"],
    ),
    (
        "Ms. Ortiz: Your report is late. The deadline was yesterday.\n"
        "Evan: I am sorry. There was a delay with the data.\n"
        "Ms. Ortiz: Is a new deadline tomorrow evening possible?\n"
        "Evan: Yes. I will send it before six.\n"
        "Ms. Ortiz: Please confirm by email in the morning.\n"
        "Evan: I will. Thank you for the extra time.",
        "Госпожа Ортис: Ваш отчёт опоздал. Крайний срок был вчера.\n"
        "Эван: Извините. Была задержка с данными.\n"
        "Госпожа Ортис: Новый срок завтра вечером возможен?\n"
        "Эван: Да. Я отправлю его до шести.\n"
        "Госпожа Ортис: Подтвердите, пожалуйста, письмом утром.\n"
        "Эван: Хорошо. Спасибо за дополнительное время.",
        ["deadline", "sorry", "delay", "tomorrow", "evening", "confirm", "morning"],
    ),
    (
        "Nina: Your music is loud after ten. I am the neighbour across the hall.\n"
        "Chris: I am sorry. I did not know it was a problem.\n"
        "Nina: The building policy says quiet hours start at ten.\n"
        "Chris: I understand. I will turn it down.\n"
        "Nina: Thank you. I did not want to make a formal complaint.\n"
        "Chris: That is fair. I agree with the rule.",
        "Нина: Ваша музыка громкая после десяти. Я соседка напротив.\n"
        "Крис: Извините. Я не знал, что это проблема.\n"
        "Нина: По правилу дома тихие часы начинаются в десять.\n"
        "Крис: Понимаю. Я сделаю тише.\n"
        "Нина: Спасибо. Я не хотела писать официальную жалобу.\n"
        "Крис: Это справедливо. Я согласен с правилом.",
        ["neighbour", "sorry", "problem", "policy", "complaint", "agree"],
    ),
    (
        "Ivy: The online service is not available, and my payment is stuck.\n"
        "Paul: I am sorry. Is the error still on the screen?\n"
        "Ivy: Yes. I have tried three times this morning.\n"
        "Paul: We have two options: wait an hour or I can move the payment now.\n"
        "Ivy: I prefer the second solution.\n"
        "Paul: Done. I will confirm by email this evening.",
        "Айви: Онлайн-услуга недоступна, и мой платёж завис.\n"
        "Пол: Извините. Ошибка всё ещё на экране?\n"
        "Айви: Да. Я пробовала три раза сегодня утром.\n"
        "Пол: Есть два варианта: подождать час или я переведу платёж сейчас.\n"
        "Айви: Я предпочитаю второе решение.\n"
        "Пол: Готово. Я подтвержу письмом сегодня вечером.",
        ["service", "available", "sorry", "morning", "option", "prefer", "solution", "confirm", "evening"],
    ),
]


def main() -> None:
    write_topic(
        "bridge_dictation_monologues",
        "Bridge: простые диктанты",
        SIMPLE_WORDS,
        SIMPLE_PHRASES,
        SIMPLE_MONOLOGUES,
    )
    write_topic(
        "bridge_dictation_dialogues",
        "Bridge: сложные диктанты",
        COMPLEX_WORDS,
        COMPLEX_PHRASES,
        COMPLEX_DIALOGUES,
    )


if __name__ == "__main__":
    main()
