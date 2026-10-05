# Отчёт: тест кнопок гарнитуры — одинарное / двойное / тройное Play

**Дата:** 2026-10-04  
**Сборка:** v1.6.18 (versionCode 46) · debug  
**Экран:** Тесты → вкладка BT Play  
**Эталон:** AHCC «Тест BT-кнопок» (`HeadsetButtonHub`)

---

## Вердикт

Одинарное, двойное и тройное Play на вкладке BT Play **работают**. Счётчики Play / Next / Stop совпадают с поведением AHCC.

---

## Жесты и ожидаемый результат

| Жест | Результат в UI | Журнал (пример) |
|------|----------------|-----------------|
| 1× Play (или Play/Pause) | счётчик **Play** +1 | `Play` / `Pause` |
| 2× Play подряд в окне серии | счётчик **Next** +1 | `Next (2×Play)` |
| 3× Play подряд в окне серии | счётчик **Stop** +1 | `Stop (3×Play)` |
| Аппаратный Next (жест Buds double) | счётчик **Next** +1 | `Next` |
| Аппаратный Previous/Stop (жест Buds triple) | счётчик **Stop** +1 | `Stop (Prev)` / `Stop` |

На многих Buds система сама мапит жесты: **single → Play/Pause**, **double → MEDIA_NEXT**, **triple → MEDIA_PREVIOUS**. Поэтому тройной жест часто приходит не как три сырых Play, а как Previous → Stop.

---

## Что изменено относительно v1.6.17

1. **Burst-модель как в AHCC** (`HeadsetButtonNotifier`)  
   Серия накапливается, зачёт после паузы settle; при `burstCount >= 3` — немедленный Stop.

2. **Фильтр эха PLAY↔PAUSE**  
   Один физический клик часто даёт пару PLAY+PAUSE (или dual delivery). Узкие пары (&lt;180 ms) не считаются вторым нажатием.

3. **Окно серии ≥ 900 ms**  
   `nextDoubleTapMs.coerceAtLeast(900)`; дефолт prefs поднят до **650 ms** (400 ms было слишком коротко для тройного на buds).

4. **MEDIA_PREVIOUS / MEDIA_STOP → Stop**  
   Как в AHCC; в журнале — `Stop (Prev)` при Previous.

5. **UI**  
   Три крупных счётчика Play / Next / Stop; подсказка обновлена под жесты Buds.

---

## Как проверяли

1. Force-stop YouTube / Spotify / других media-конкурентов.  
2. AndEngTutor → **Окно тестов** → вкладка **BT Play** → Reassert MediaSession.  
3. На гарнитуре: 1× / 2× / 3× Play (или штатные жесты Buds).  
4. Наблюдали счётчики и строки журнала HARDWARE / SIMULATED.

### Результат приёмки

| Сценарий | Статус |
|----------|--------|
| 1× → Play | OK |
| 2× → Next | OK |
| 3× → Stop | OK |

---

## Ключевые файлы

- `app/src/main/java/com/englishtutor/session/HeadsetButtonNotifier.kt` — burst, echo, Previous→Stop  
- `app/src/main/java/com/englishtutor/session/HeadsetTestController.kt` — счётчик `stopCount`  
- `app/src/main/java/com/englishtutor/session/HeadsetButtonPreferences.kt` — окно серии  
- `app/src/main/java/com/englishtutor/ui/screens/voicetest/VoiceTestScreen.kt` — UI Play/Next/Stop  
- Эталон AHCC: `HeadsetButtonHub.kt` / экран «Тест BT-кнопок»

---

## Условия для стабильного HARDWARE

- Пауза у Spotify/YouTube перед тестом.  
- Native capture ON (MediaSession) после Reassert.  
- При конфликте Tasker / чужой media-button session — закрыть конкурента и снова Reassert.
