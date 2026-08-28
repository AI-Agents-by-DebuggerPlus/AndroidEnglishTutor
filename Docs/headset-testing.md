# Тест кнопок Bluetooth-гарнитуры (AndroidEnglishTutor)

**Версия:** 1.3.3+  
**Эталон UI/логики:** AndroidChat `TestsScreen` → вкладка BT Play

---

## Быстрый старт

1. Подключите BT-гарнитуру к телефону.
2. **Выключите** профиль Tasker **BT Headset Play Pause** (если используется) — иначе конфликт MediaSession.
3. Откройте приложение → **Окно тестов (речь + гарнитура)**.
4. Перейдите на вкладку **BT Play**.
5. Убедитесь:
   - `Native capture: ON (MediaSession)`
   - Уведомление «BT Play (English Tutor)»
6. Нажмите **Симулировать Play** — счётчик должен стать `1`.
7. Нажмите Play на гарнитуре — счётчик растёт, строка в журнале.

---

## Вкладки окна тестов

| Вкладка | Назначение |
|---------|------------|
| **TTS** | Озвучка произвольного текста |
| **STT** | Запись и распознавание речи |
| **BT Play** | Тест media-кнопок гарнитуры (изоляция от урока) |

На вкладке BT Play **не** запускаются TTS/STT урока — только счётчик Play и журнал (как в AndroidChat).

---

## UI вкладки BT Play

| Элемент | Описание |
|---------|----------|
| Статус capture | ON/OFF — активен ли `HeadsetTestService` |
| Большой счётчик | Число нажатий Play / Play-Pause / HeadsetHook |
| Последнее событие | Метка + время `HH:mm:ss.SSS` |
| Симулировать Play | Проверка без гарнитуры |
| Журнал событий | До 40 строк: `12:34:56.789  Play  (#3)` |
| ↻ в шапке | Сброс счётчика и журнала |

---

## Архитектура

```
Гарнитура (AVRCP)
        │
        ▼
HeadsetTestService (foreground, MediaSessionCompat)
        │  onPlay / onPause / onSkipToNext / onMediaButtonEvent
        ▼
HeadsetTestController.notifyButton(label)
        │  debounce 500 ms
        │  только Play-эквиваленты → счётчик + UI
        ▼
VoiceTestScreen (вкладка BT Play)
```

### Ключевые файлы

- `session/HeadsetTestService.kt` — MediaSession, паттерн AndroidChat `HeadsetMonitorService`
- `session/HeadsetTestController.kt` — состояние теста
- `session/HeadsetButtonNames.kt` — KeyEvent → `MEDIA_PLAY`, `HEADSETHOOK`, …
- `ui/screens/voicetest/VoiceTestScreen.kt` — 3 вкладки

### Автозапуск / остановка

- Вкладка **BT Play** выбрана → `HeadsetTestService.start()`
- Другая вкладка или выход с экрана → `HeadsetTestService.stop()`
- При старте теста останавливается `LessonSessionService` (избежание двух MediaSession)

---

## Отличия от урока

| | Урок (`LessonSessionService`) | Тест (`HeadsetTestService`) |
|--|-------------------------------|------------------------------|
| Audio focus | Да (TTS/STT) | Нет (как AndroidChat) |
| Playback state | PLAYING / PAUSED по фазе | `STATE_PAUSED` |
| TTS при кнопке | Да | Нет |
| Play-эквиваленты | play/pause логика урока | Только счётчик |

---

## Логи

Экран **Логи** → категория `Headset`:

```
[LOG:Headset] BT button Play via native
[LOG:Headset] Headset test ACTIVE
```

Отправка на сервер: кнопка **Отправить логи на сервер** (Supabase, см. README).

---

## Устранение неполадок

| Симптом | Действие |
|---------|----------|
| Симуляция работает, гарнитура нет | Выключить Tasker / другое медиа-приложение |
| Capture OFF | Переключиться на вкладку BT Play заново |
| Счётчик не растёт | Проверить уведомление foreground-сервиса |
| Двойной STOP в логах | Нормально при выходе с экрана (stop + DisposableEffect) |

---

## См. также

- [AndroidChat-BT-Headset-Testing-Report.md](AndroidChat-BT-Headset-Testing-Report.md) — полный разбор AndroidChat
- `TaskerToWpf/Docs/Known-Issues.md` — конфликты MediaSession с Tasker
