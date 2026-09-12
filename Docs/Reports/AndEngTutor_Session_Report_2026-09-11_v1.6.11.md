# Отчёт по сессии: AndEngTutor v1.6.11

**Дата:** 2026-09-11  
**Сборка:** v1.6.11 (versionCode 39) · debug  
**Устройство:** Pixel 6a + Pixel Buds Pro 2  
**Лог:** `Files/Logs/androidenglishtutor.log` (экспорт ~20:48–20:49, события устройства ~20:42–20:48)

---

## Вердикт

Пользовательский сценарий сессии прошёл успешно: crash/ANR нет, озвучка и распознавание работали, голосовой тест закрыл вопросы с верными ответами. В логах есть WARN/ERROR по A2DP-готовности и пустым STT — они не сорвали сценарий.

---

## Что проверяли

1. **Тесты → «Озвучка / A2DP»** — Play → запись → STT → TTS распознанного текста  
2. **Голосовой тест** — Next/Play с гарнитуры (HARDWARE media buttons)

---

## Результаты по логам

| Метрика | Значение |
|---------|----------|
| FATAL / crash / Exception | 0 |
| Успешные STT (`STT result=`) | 6 |
| STT error code=7 (нет речи / timeout) | 5 |
| Quiz `match=true` | 5 |
| Quiz `match=false` | 0 |
| Recognize+speak (вкладка озвучки) | 1 старт, STT+TTS OK |
| Новые угаданные слова за сессию | +5 (total 8→12) |
| A2DP wait timed out | ~28 (повторные ожидания) |

### Успешные распознавания

| Время (устройство) | Контекст | Результат |
|--------------------|----------|-----------|
| 20:42:43 | VoiceTest Recognize+speak | `Monday Tuesday Wednesday Thursday Friday Saturday Sunday` → озвучено |
| 20:43:52 | Quiz | `Water` → правильно |
| 20:44:25 | Quiz | `Book` → правильно |
| 20:45:03 | Quiz | `Good night` → правильно |
| 20:47:48 | Quiz | `One` → правильно |
| 20:48:23 | Quiz | `Good afternoon` → правильно |

HARDWARE-кнопки Play/Next стабильно доставлялись в quiz / isolated handler.

---

## Наблюдения (не блокеры UX)

1. **`readyForMediaTts=false` при `a2dpOn=true` и `a2dpOut=Pixel Buds Pro 2`**  
   После SCO Android оставляет `comm=EARPIECE:Pixel 6a`. Хелпер ждёт очистки phone comm → timeout → WARN «may use phone speaker». На практике TTS на Buds слышен (пользователь: «всё сработало»).

2. **STT error code=7**  
   Пустые попытки (нет речи / слишком тихо / timeout). Обрабатываются как «Не правильно.» / повтор Play — ожидаемое поведение квиза.

3. **Задержка feedback**  
   Из‑за ожидания A2DP (~4 с) ответ «Правильно!» / «Не правильно.» приходит с паузой после STT.

---

## Изменения в этой сборке (кратко)

- Пул слов теста: любые слова банка по уровню пользователя (не только из уроков)
- Вкладка **«Озвучка / A2DP»**: Play = STT→TTS + индикаторы маршрута (A2DP, SCO, mode, comm, outputs)

---

## Следующая задача

См. `Docs/Tasks/Cursor/AndEngTutor_Answer_Ack_Beep_2026-09-12.md` — короткий звуковой сигнал после приёма ответа пользователя.
