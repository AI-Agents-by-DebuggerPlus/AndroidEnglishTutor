# Тест кнопок Bluetooth-гарнитуры (AndroidEnglishTutor)

**Версия:** 1.3.8+  
**Эталон:** AndroidChat `HeadsetMonitorService` + `HeadsetButtonNotifier`

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
| Статус capture | ON/OFF — активен ли `HeadsetMonitorService` (старт при запуске приложения) |
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
HeadsetMonitorService (foreground, MediaSessionCompat) — sticky, как AndroidChat
        │  onPlay / onPause / onMediaButtonEvent
        ▼
HeadsetButtonNotifier.notifyButton(label)
        │
        ├─ btPlayTestIsolation? → счётчик (+ optional STT handler на вкладке STT)
        └─ production → EnglishTutorPlayHandler
        │  debounce 500 ms
        │  только Play-эквиваленты → счётчик + UI
        ▼
VoiceTestScreen (вкладка BT Play)
```

### Ключевые файлы

- `session/HeadsetMonitorService.kt` — sticky MediaSession (как AndroidChat)
- `session/HeadsetButtonNotifier.kt` — debounce + isolation + routing
- `session/HeadsetTestController.kt` — счётчик UI
- `session/HeadsetButtonNames.kt` — KeyEvent → `MEDIA_PLAY`, `HEADSETHOOK`, …
- `ui/screens/voicetest/VoiceTestScreen.kt` — 3 вкладки

### Жизненный цикл

- **Запуск приложения** → `HeadsetMonitorService.start()` (sticky)
- **Окно тестов открыто** → `btPlayTestIsolation = true`
- **Окно тестов закрыто** → isolation OFF → кнопки в урок
- Вкладка BT Play **не** стартует/останавливает сервис

---

## Отличия от урока

| | Урок (`LessonSessionService`) | Тест (isolation) |
|--|-------------------------------|------------------|
| MediaSession | `HeadsetMonitorService` (общий) | тот же сервис |
| Audio focus | Да (TTS/STT) | Нет (как AndroidChat) |
| Play-эквиваленты | `EnglishTutorPlayHandler` | Только счётчик |

### BT Play в уроке (`EnglishTutorPlayHandler`)

| Состояние | Действие Play |
|-----------|---------------|
| STT слушает | cancel |
| TTS фразы на паузе | cue «Continue» → resume |
| TTS фразы играет | pause + cue «Pause» |
| Idle, фраза не озвучена | speak phrase |
| Idle, фраза озвучена | STT (Bluetooth SCO) |
| Пустой STT | cue «Play» → next phrase |

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

- [AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md](AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md) — полный отчёт по BT из AndroidChat
- [AndroidChat-BT-Headset-Testing-Report.md](AndroidChat-BT-Headset-Testing-Report.md) — полный разбор AndroidChat
- `TaskerToWpf/Docs/Known-Issues.md` — конфликты MediaSession с Tasker
