# Тест кнопок Bluetooth-гарнитуры (AndEngTutor)

**Версия:** 1.5.1+ (versionCode 22)  
**Отображаемое имя:** AndEngTutor · пакет `com.englishtutor`  
**Эталон:** BT_Play / `AndroidChatBtTest95` (AudioFocus + USAGE_MEDIA pulse + reassert)

Подтверждено на Pixel 6a + Pixel Buds Pro 2 (2026-09-06): физический Play → **HARDWARE** в логе.

Отчёты:

- [Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md](Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md)
- [Reports/AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md](Reports/AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md)

---

## Быстрый старт

1. Подключите BT-гарнитуру.
2. Force-stop конкурентов (YouTube, музыкальные плееры, BtTest*):
   ```bash
   adb shell am force-stop com.google.android.youtube
   ```
3. Отключите Tasker-профиль **BT Key / Grab**, если включён.
4. Откройте **AndEngTutor** → **Окно тестов** (вкладка **BT Play** открывается по умолчанию).
5. Дождитесь cue *«BT test ready»* и (тихого) USAGE_MEDIA pulse.
6. Статус: `Native capture: ON`.
7. **Симулировать Play** → журнал `[SIMULATED]`.
8. Play на гарнитуре → журнал `[HARDWARE]`, счётчик +1.
9. Если Play «молчит» после YouTube → **Reassert MediaSession**.

Проверка системы:

```bash
adb shell dumpsys media_session
# Ожидание: Media button session is com.englishtutor/AndEngTutorHeadset/...
```

---

## UI вкладки BT Play

| Элемент | Описание |
|---------|----------|
| Native capture | ON/OFF — `HeadsetMonitorService` + MediaSession |
| Счётчик | Play / Play-Pause / HeadsetHook |
| Последнее событие | Метка + HARDWARE/SIMULATED + время |
| Reassert MediaSession | Force reattach + AudioFocus + USAGE_MEDIA pulse + cue |
| Симулировать Play | Без гарнитуры → SIMULATED |
| Журнал | До 40 строк: `HH:mm:ss.SSS  [HARDWARE]  Play  (#n)` |
| Диагностика (внизу) | Разрешения, Tasker, Media-button #1, ActiveSessions |
| Notification Access | Для списка Active MediaSessions (кто #1) |

---

## Архитектура (v1.5.1)

```
Гарнитура (AVRCP Play)
        │
        ▼
Android media-button session  ← должна быть com.englishtutor
        │
        ▼
HeadsetMonitorService (FGS mediaPlayback)
  • requestAudioFocus(USAGE_MEDIA)
  • PLAYING → PAUSED claim
  • USAGE_MEDIA AudioTrack pulse (UID приложения)
  • reassert / ACL force reattach (main thread)
        │
        ▼
HeadsetButtonNotifier (debounce 500 ms, HARDWARE/SIMULATED)
        │
        ├─ isolation ON (экран тестов) → счётчик / журнал
        └─ isolation OFF → EnglishTutorPlayHandler → урок
```

**Важно:** cue через Google TTS сам по себе **не** назначает media-button session приложению (playback уходит в `com.google.android.tts`). Нужен pulse под UID `com.englishtutor`.

### Ключевые файлы

| Файл | Роль |
|------|------|
| `session/HeadsetMonitorService.kt` | FGS, AudioFocus, claim, reassert, pulse |
| `session/MediaPlaybackPulse.kt` | Короткий USAGE_MEDIA AudioTrack |
| `session/HeadsetButtonNotifier.kt` | Debounce, isolation, HARDWARE/SIMULATED |
| `session/HeadsetTestController.kt` | Счётчик UI |
| `bluetooth/ActiveSessionsHelper.kt` | Кто #1 MediaSession |
| `bluetooth/NoOpNotificationListener.kt` | Notification Access |
| `bluetooth/HeadsetDiagnosticsHelper.kt` | Карточка диагностики |
| `ui/screens/voicetest/*` | Tests UI, Reassert, cue |

### Жизненный цикл

- **Холодный старт** → `HeadsetMonitorService.start()` в `Application`
- **Окно тестов** → isolation ON, `reassert(speakCue=true)` + pulse
- **Вкладка BT Play** → повторный reassert при входе
- **ACL connect/disconnect** → force reattach
- **Закрытие тестов** → isolation OFF → кнопки в урок

---

## Типичные сбои

| Симптом | Причина | Действие |
|---------|---------|----------|
| Simulate OK, HARDWARE нет | Чужая / null media-button session | Reassert; force-stop YouTube; dumpsys |
| Media button session = null | Нет playback от UID приложения | Pulse при reassert (уже в 1.5.1) |
| Tasker WARN в диагностике | Grab media keys | Выключить BT Key |
| После YouTube Play «молчит» | YouTube = #1 | Reassert |

---

## Чеклист приёмки

- [x] Физический Play → HARDWARE + счётчик (подтверждено 2026-09-06)
- [x] Simulate → SIMULATED
- [x] Reassert + USAGE_MEDIA pulse
- [x] dumpsys: Media button session = `com.englishtutor`
- [x] Isolation не ломает lesson flow вне теста
- [ ] Notification Access (опционально, для UI #1)
- [ ] POST_NOTIFICATIONS выдан (рекомендуется на API 33+)
