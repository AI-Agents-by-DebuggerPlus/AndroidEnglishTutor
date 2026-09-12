# Задача: голосовой мини-тест по командам Next / Play

**Когда:** 2026-09-11  
**Проект:** AndEngTutor (`com.englishtutor`)  
**Статус:** DONE (v1.6.0 / versionCode 28)

---

## Цель

Реализовать простой голосовой тест фраз, управляемый кнопками гарнитуры:

| Кнопка | Действие |
|--------|----------|
| **Next** | Старт теста / повтор вопроса / переход после верного ответа |
| **Play** | Запись ответа (STT); при ошибке — повторная запись |

Голосовые подсказки TTS — **на русском**.

---

## Реализация

- `session/VoiceQuizController.kt` + `VoiceQuizModels.kt` / `VoiceQuizBank`
- Маршрутизация: `EnglishTutorPlayHandler` → quiz, если `isActive`
- UI: Home → **Голосовой тест** → `VoiceQuizScreen`
- Вопросы: Привет→hello/hi; Спасибо→thank you/thanks; Пока→bye/goodbye
- После ошибки Next игнорируется; только Play
- Matching: `PronunciationMatcher` ≥ 0.7 по любому accepted answer

---

## Критерии приёмки

- [x] Next → русский вопрос озвучен
- [x] Play → STT → «Правильно!» / «Не правильно.»
- [x] После «Правильно!» Next → следующий вопрос
- [x] После ошибки Play → повтор; Next игнор
- [x] HARDWARE через существующий MediaSession (reassert при входе)
- [x] Не ломает BT Play tab (isolation остаётся для VoiceTest)
