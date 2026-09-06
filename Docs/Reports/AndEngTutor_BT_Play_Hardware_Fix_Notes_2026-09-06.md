# AndEngTutor BT Play Hardware Fix — Notes 2026-09-06

Связано с:  
[AndEngTutor_BT_Play_Hardware_Fix_Agent_Instruction.md](../Tasks/Cursor/AndEngTutor_BT_Play_Hardware_Fix_Agent_Instruction.md)  
[AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md](./AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md)

**Сборка после фикса:** v1.5.1 (22)

---

## Фаза A — dumpsys / A/B

### A1. Конкуренты (force-stop)

Остановлены: YouTube, YT Music, Spotify, MP3 player, AndroidChat*, BtTest*, AndEngTutor (перед стартом).

На устройстве также присутствовали: `com.spotify.music`, `com.music.player.mp3player.white`, `com.taskertowpf.androidchat`.

### A2. Tutor до фикса (v1.5.0)

После `am start com.englishtutor/.MainActivity`:

```text
Media key listener: null
Last MediaButtonReceiver: null
Media button session is null
Sessions Stack - have 1 sessions:
  AndEngTutorHeadset com.englishtutor/... active=true state=PAUSED
Audio playback (lastly played):
  uid=... packages=com.google.android.tts
  uid=... packages=com.taskertowpf.androidchat
```

**Вывод:** MediaSession у Tutor **active**, но **не** назначена media-button session.  
TTS cue идёт через `com.google.android.tts` (чужой UID) → система не считает, что Tutor «играл» media.

### A4. A/B с AndroidChatBtTest95

После старта BtTest95:

```text
Media button session is com.taskertowpf.androidchatbttest95/AndroidChatBtTest95Headset/...
Sessions Stack: AndroidChatBtTest95Headset active=true PAUSED
Audio playback: uid=... packages=com.taskertowpf.androidchatbttest95
```

| Tutor HARDWARE | 95 media-button session | Вывод |
|---|---|---|
| нет (раньше) | **да** (95 = #1) | **code gap в Tutor** → Фаза C |

---

## Фаза C — что изменено (v1.5.1)

### Root cause

Cue через Google TTS **не** регистрирует playback под `com.englishtutor`.  
BtTest95 появляется в `Audio playback` своим пакетом и поэтому получает media-button session.

### Fix

1. **`MediaPlaybackPulse`** — короткий тихий 440 Hz через `AudioTrack` + `AudioAttributes.USAGE_MEDIA` под UID приложения.
2. При **force reassert** в `HeadsetMonitorService`: pulse → повторный claim PLAYING→PAUSED на main thread.
3. Лог: `USAGE_MEDIA pulse done` / `MediaSession claim refreshed after USAGE_MEDIA pulse`.

### Фаза D — диагностика #1

- `NoOpNotificationListener` + Manifest.
- `ActiveSessionsHelper` (пакеты, rank, competitor notes).
- В карточке диагностики: кнопка **Открыть Notification Access**, список Active MediaSessions.
- Строка **Media-button #1** в diagnostics.

---

## Как проверить приёмку

1. Force-stop YouTube / BtTest95 / music.
2. AndEngTutor → Тесты → BT Play → Reassert → дождаться cue + (тихого) pulse.
3. `adb shell dumpsys media_session` → ожидание:  
   `Media button session is com.englishtutor/...`
4. Play на Pixel Buds → лог HARDWARE + счётчик +1.
5. Simulate → SIMULATED (регрессия).
6. (Опционально) включить Notification Access → в диагностике #1 = AndEngTutor.

---

## Проверка после установки v1.5.1

```text
Media button session is com.englishtutor/AndEngTutorHeadset/725
AndEngTutorHeadset ... active=true
Audio playback:
  uid=... packages=com.google.android.tts
  uid=... packages=com.englishtutor
```

**Tutor теперь media-button session** (gap закрыт на уровне dumpsys).  
**Физический Play подтверждён пользователем и логом** (14:04, ≥10× HARDWARE via `hardware-mediaButtonEvent`).

Пример:

```text
[MediaPulse] USAGE_MEDIA pulse done (180ms)
[HeadsetMonitor] MediaSession claim refreshed after USAGE_MEDIA pulse
[Headset] BT button Play via hardware-mediaButtonEvent (HARDWARE)
[Headset] HARDWARE: MEDIA_PLAY via hardware-mediaButtonEvent → isolation (counter only)
```

---

## Итог

**Доказано:** у Tutor media-button session была `null`, у BtTest95 — своя.  
**Причина gap:** TTS cue не даёт USAGE_MEDIA playback от UID Tutor.  
**Фикс:** USAGE_MEDIA AudioTrack pulse при reassert + ActiveSessions UI.  
**dumpsys после фикса:** Media button session = `com.englishtutor`.  
**Приёмка:** HARDWARE Play на Pixel Buds Pro 2 — OK (v1.5.1).
