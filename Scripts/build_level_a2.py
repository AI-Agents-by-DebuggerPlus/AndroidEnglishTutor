"""Original A2 course: everyday talk, past, plans, comparisons, travel.

Wording is written for this app.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "topics" / "A2" / "level_a2.json"

WORDS = [
    ("yesterday", "вчера"),
    ("weekend", "выходные"),
    ("month", "месяц"),
    ("year", "год"),
    ("often", "часто"),
    ("usually", "обычно"),
    ("sometimes", "иногда"),
    ("never", "никогда"),
    ("always", "всегда"),
    ("early", "рано"),
    ("late", "поздно"),
    ("cheap", "дешёвый"),
    ("expensive", "дорогой"),
    ("fast", "быстрый"),
    ("slow", "медленный"),
    ("easy", "лёгкий"),
    ("difficult", "трудный"),
    ("quiet", "тихий"),
    ("noisy", "шумный"),
    ("comfortable", "удобный"),
    ("famous", "известный"),
    ("airport", "аэропорт"),
    ("plane", "самолёт"),
    ("train", "поезд"),
    ("ticket", "билет"),
    ("hotel", "гостиница"),
    ("passport", "паспорт"),
    ("luggage", "багаж"),
    ("left", "налево"),
    ("right", "направо"),
    ("straight", "прямо"),
    ("near", "рядом"),
    ("far", "далеко"),
    ("corner", "угол"),
    ("headache", "головная боль"),
    ("cough", "кашель"),
    ("pharmacy", "аптека"),
    ("doctor", "врач"),
    ("appointment", "запись к врачу"),
    ("rest", "отдых"),
    ("pain", "боль"),
    ("tired", "уставший"),
    ("healthy", "здоровый"),
    ("size", "размер"),
    ("price", "цена"),
    ("discount", "скидка"),
    ("cash", "наличные"),
    ("receipt", "чек"),
    ("customer", "покупатель"),
    ("message", "сообщение"),
    ("call", "звонок"),
    ("email", "электронная почта"),
    ("meeting", "встреча"),
    ("opinion", "мнение"),
    ("because", "потому что"),
    ("neighbor", "сосед"),
    ("bill", "счёт"),
    ("repair", "ремонт"),
    ("menu", "меню"),
    ("order", "заказ"),
    ("waiter", "официант"),
    ("delicious", "вкусный"),
    ("spicy", "острый"),
    ("boss", "начальник"),
    ("colleague", "коллега"),
    ("salary", "зарплата"),
    ("schedule", "расписание"),
    ("office", "офис"),
    ("break", "перерыв"),
    ("happy", "счастливый"),
    ("sad", "грустный"),
    ("angry", "злой"),
    ("worried", "обеспокоенный"),
    ("surprised", "удивлённый"),
    ("bored", "скучающий"),
    ("interested", "заинтересованный"),
    ("need", "нуждаться"),
    ("visit", "посещать"),
    ("travel", "путешествовать"),
    ("arrive", "прибывать"),
    ("leave", "уезжать"),
    ("miss", "скучать / пропускать"),
    ("remember", "помнить"),
    ("forget", "забывать"),
    ("decide", "решать"),
    ("plan", "планировать"),
    ("hope", "надеяться"),
    ("prefer", "предпочитать"),
    ("explain", "объяснять"),
    ("ask", "спрашивать"),
    ("answer", "отвечать"),
    ("wait", "ждать"),
    ("help", "помогать"),
    ("spend", "тратить"),
    ("save", "копить"),
    ("wear", "носить одежду"),
    ("choose", "выбирать"),
    ("return", "возвращать"),
    ("agree", "соглашаться"),
]

PHRASES = [
    ("last weekend", "в прошлые выходные", ["weekend"]),
    ("yesterday morning", "вчера утром", ["yesterday"]),
    ("once a month", "раз в месяц", ["month"]),
    ("too expensive", "слишком дорого", ["expensive"]),
    ("a cheap ticket", "дешёвый билет", ["cheap"]),
    ("a fast train", "быстрый поезд", ["fast", "train"]),
    ("a quiet hotel", "тихая гостиница", ["quiet", "hotel"]),
    ("a noisy street", "шумная улица", ["noisy"]),
    ("turn left", "поверните налево", ["left"]),
    ("turn right", "поверните направо", ["right"]),
    ("go straight", "идите прямо", ["straight"]),
    ("near the corner", "рядом с углом", ["near", "corner"]),
    ("far from the airport", "далеко от аэропорта", ["far", "airport"]),
    ("a bad headache", "сильная головная боль", ["headache"]),
    ("a dry cough", "сухой кашель", ["cough"]),
    ("at the pharmacy", "в аптеке", ["pharmacy"]),
    ("see a doctor", "сходить к врачу", ["doctor"]),
    ("the right size", "нужный размер", ["size"]),
    ("a good price", "хорошая цена", ["price"]),
    ("pay in cash", "платить наличными", ["cash"]),
    ("keep the receipt", "сохраните чек", ["receipt"]),
    ("a short message", "короткое сообщение", ["message"]),
    ("a phone call", "телефонный звонок", ["call"]),
    ("a work meeting", "рабочая встреча", ["meeting", "office"]),
    ("in my opinion", "по моему мнению", ["opinion"]),
    ("the electricity bill", "счёт за электричество", ["bill"]),
    ("home repair", "ремонт дома", ["repair"]),
    ("a spicy dish", "остроё блюдо", ["spicy"]),
    ("a delicious lunch", "вкусный обед", ["delicious"]),
    ("my colleague", "мой коллега", ["colleague"]),
    ("a busy schedule", "плотное расписание", ["schedule"]),
    ("a coffee break", "перерыв на кофе", ["break"]),
    ("feel tired", "чувствовать усталость", ["tired"]),
    ("feel worried", "беспокоиться", ["worried"]),
    ("travel by plane", "лететь самолётом", ["travel", "plane"]),
    ("arrive on time", "прибыть вовремя", ["arrive"]),
    ("leave early", "уехать рано", ["leave", "early"]),
    ("forget the passport", "забыть паспорт", ["forget", "passport"]),
    ("save money", "копить деньги", ["save"]),
    ("spend the weekend", "провести выходные", ["spend", "weekend"]),
]

SENTENCES = [
    ("Yesterday I visited my neighbor.", "Вчера я навестил соседа.", ["yesterday", "visit", "neighbor"]),
    ("Last weekend we traveled by train.", "В прошлые выходные мы ездили на поезде.", ["weekend", "travel", "train"]),
    ("I usually get up early, but yesterday I was late.", "Я обычно встаю рано, но вчера опоздал.", ["usually", "early", "yesterday", "late"]),
    ("We sometimes eat out, but we never stay late.", "Мы иногда едим вне дома, но никогда не засиживаемся допоздна.", ["sometimes", "never", "late"]),
    ("The hotel was quiet and comfortable.", "Гостиница была тихой и удобной.", ["hotel", "quiet", "comfortable"]),
    ("This ticket is cheaper than the plane.", "Этот билет дешевле, чем самолёт.", ["cheap", "plane"]),
    ("The train is fast, but the ticket is expensive.", "Поезд быстрый, но билет дорогой.", ["train", "fast", "ticket", "expensive"]),
    ("Turn left at the corner and go straight.", "На углу поверните налево и идите прямо.", ["left", "corner", "straight"]),
    ("The airport is far, so we leave early.", "Аэропорт далеко, поэтому мы выезжаем рано.", ["airport", "far", "leave", "early"]),
    ("I forgot my passport at home.", "Я забыл паспорт дома.", ["forget", "passport"]),
    ("Please wait near the station.", "Пожалуйста, подождите рядом со станцией.", ["wait", "near"]),
    ("I have a headache and a cough.", "У меня болит голова и кашель.", ["headache", "cough"]),
    ("I need an appointment with the doctor.", "Мне нужна запись к врачу.", ["need", "appointment", "doctor"]),
    ("You can buy medicine at the pharmacy.", "Лекарство можно купить в аптеке.", ["pharmacy"]),
    ("Rest today, and you will feel better.", "Отдохните сегодня, и вам станет лучше.", ["rest"]),
    ("This size is small. Can I try a bigger one?", "Этот размер маленький. Можно примерить побольше?", ["size"]),
    ("The price is good because there is a discount.", "Цена хорошая, потому что есть скидка.", ["price", "because", "discount"]),
    ("I prefer to pay in cash.", "Я предпочитаю платить наличными.", ["prefer", "cash"]),
    ("The customer asked for a receipt.", "Покупатель попросил чек.", ["customer", "ask", "receipt"]),
    ("I can return it if it does not fit.", "Я могу вернуть это, если не подойдёт.", ["return"]),
    ("She sent me a message, but I did not answer.", "Она прислала сообщение, но я не ответил.", ["message", "answer"]),
    ("Call me after the meeting.", "Позвони мне после встречи.", ["call", "meeting"]),
    ("I agree with your opinion.", "Я согласен с твоим мнением.", ["agree", "opinion"]),
    ("The bill is high because the repair was difficult.", "Счёт большой, потому что ремонт был трудным.", ["bill", "because", "repair", "difficult"]),
    ("The waiter explained the menu.", "Официант объяснил меню.", ["waiter", "explain", "menu"]),
    ("I ordered a spicy dish, and it was delicious.", "Я заказал острое блюдо, и оно было вкусным.", ["order", "spicy", "delicious"]),
    ("My boss changed the schedule.", "Начальник изменил расписание.", ["boss", "schedule"]),
    ("I hope my salary is higher next year.", "Надеюсь, в следующем году зарплата будет выше.", ["hope", "salary", "year"]),
    ("We decided to spend the weekend at home.", "Мы решили провести выходные дома.", ["decide", "spend", "weekend"]),
    ("I was surprised and a little angry.", "Я удивился и немного разозлился.", ["surprised", "angry"]),
    ("She feels sad because she misses her family.", "Ей грустно, потому что она скучает по семье.", ["sad", "because", "miss"]),
    ("Are you interested in this job?", "Тебе интересна эта работа?", ["interested"]),
    ("I always remember faces, but I often forget names.", "Я всегда помню лица, но часто забываю имена.", ["always", "remember", "often", "forget"]),
    ("Choose a healthy lunch and save money.", "Выбери полезный обед и сэкономь деньги.", ["choose", "healthy", "save"]),
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
        "id": "level_a2",
        "titleRu": "Уровень A2",
        "words": words,
        "phrases": cards(PHRASES),
        "sentences": cards(SENTENCES),
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"words={len(words)} phrases={len(PHRASES)} sentences={len(SENTENCES)} -> {OUT}")


if __name__ == "__main__":
    main()
