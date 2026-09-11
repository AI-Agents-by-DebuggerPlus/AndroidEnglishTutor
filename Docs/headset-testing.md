# Тест кнопок Bluetooth-гарнитуры (AndEngTutor)

**Версия:** 1.5.6 (versionCode 27)  
**Отображаемое имя:** AndEngTutor · пакет `com.englishtutor`  
**Устройства:** Pixel 6a + Pixel Buds Pro 2 (подтверждено 2026-09-06…2026-09-10)

Отчёты:

- [Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md](Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md) — **проблемы, решения, порт в другое приложение**
- [Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md](Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md)
- [Reports/AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md](Reports/AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md)

---

## Быстрый старт

1. Подключите BT-гарнитуру.
2. Force-stop конкурентов (YouTube и т.п.):
   ```bash
   adb shell am force-stop com.google.android.youtube
   ```
3. Отключите Tasker-профиль **BT Key / Grab**, если включён.
4. **AndEngTutor** → **Окно тестов** (вкладка **BT Play** по умолчанию).
5. Cue *«BT test ready»* + тихий USAGE_MEDIA pulse.
6. `Native capture: ON`.
7. **Симулировать Play** → `[SIMULATED]`.
8. Одиночный Play на гарнитуре → счётчик **Play** + `[HARDWARE]`.
9. Двойное нажатие (жест Next на Buds) → счётчик **Next**; companion Play подавляется.
10. При «молчании» после YouTube → **Reassert MediaSession**.

```bash
adb shell dumpsys media_session
# Ожидание: Media button session is com.englishtutor/.../AndEngTutorHeadset
```

---

## UI вкладки BT Play

| Элемент | Описание |
|---------|----------|
| Native capture | ON/OFF — FGS + MediaSession |
| Защита от повторного нажатия | Switch + интервал мс (SharedPreferences) |
| Интервал Next | Окно двойного жеста / suppress Play после Next, мс |
| Счётчики **Play** / **Next** | Рядом; Reset обнуляет оба |
| Reassert / Simulate | Force claim / UI-событие без гарнитуры |
| Журнал | До 40 строк HARDWARE/SIMULATED |
| Диагностика | Разрешения, Tasker, Media-button #1, ActiveSessions |

---

## Архитектура (v1.5.6)

```
Гарнитура (AVRCP / KeyEvent)
        │
        ▼
Android media-button session  ← UID com.englishtutor (claim + pulse)
        │
        ▼
HeadsetMonitorService (FGS mediaPlayback)
  • AudioFocus USAGE_MEDIA
  • PLAYING → PAUSED claim
  • MediaPlaybackPulse (AudioTrack USAGE_MEDIA ~180 ms)
  • onMediaButtonEvent / onPlay / onSkipToNext → notifyButton
        │
        ▼
HeadsetButtonNotifier
  • HARDWARE vs SIMULATED
  • debounce (опц.) после commit
  • Play/Pause жест: wait nextDoubleTapMs → Play; 2-й жест → Next
  • MEDIA_NEXT → Next + suppress companion Play
        │
        ├─ isolation ON (тесты) → HeadsetTestController (счётчики)
        └─ isolation OFF → EnglishTutorPlayHandler → урок
```

**Важно:** TTS cue (`com.google.android.tts`) **не** делает приложение media-button session #1. Нужен pulse под UID приложения.

### Ключевые файлы

| Файл | Роль |
|------|------|
| `session/HeadsetMonitorService.kt` | FGS, session, AudioFocus, reassert |
| `session/MediaPlaybackPulse.kt` | USAGE_MEDIA pulse |
| `session/HeadsetButtonNotifier.kt` | Debounce, Next, suppress |
| `session/HeadsetButtonPreferences.kt` | Persist настроек |
| `session/HeadsetTestController.kt` | Play/Next + журнал |
| `session/HeadsetButtonNames.kt` | KeyCode → label, жесты |
| `ui/screens/voicetest/*` | UI теста |
| `bluetooth/*` | Диагностика ActiveSessions / Tasker |

### Настройки (`headset_button_prefs`)

| Ключ | Default | Смысл |
|------|---------|--------|
| `debounce_enabled` | `true` | Игнор повторов после commit |
| `debounce_interval_ms` | `500` | Окно защиты |
| `next_double_tap_ms` | `400` | Окно Next + suppress Play после Next |

---

## Проблемы и решения (кратко)

| Проблема | Решение |
|----------|---------|
| Simulate OK, HARDWARE нет; `Media button session is null` | USAGE_MEDIA `AudioTrack` pulse при reassert (v1.5.1) |
| Двойной жест → только Play | Buds шлёт `MEDIA_NEXT` (+ часто `MEDIA_PLAY`); считать NEXT; Play/Pause как жест (v1.5.5) |
| Next и Play вместе | После Next suppress Play на `nextDoubleTapMs` (v1.5.6) |
| Случайные повторы | Debounce switch + интервал |

Подробности и код для порта: [Porting Guide](Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md).

---

## Типичные сбои

| Симптом | Действие |
|---------|----------|
| HARDWARE нет | Reassert; force-stop YouTube; dumpsys |
| Tasker WARN | Выключить Grab / BT Key |
| Next + Play оба | Нужна v1.5.6+ (suppress) |

---

## Чеклист приёмки

- [x] Физический Play → HARDWARE + счётчик Play
- [x] Simulate → SIMULATED
- [x] Reassert + USAGE_MEDIA pulse; dumpsys = `com.englishtutor`
- [x] Двойной жест Buds → Next; companion Play suppressed (v1.5.6, лог 2026-09-10)
- [x] Isolation не ломает lesson вне теста
- [ ] Notification Access (опционально)
- [ ] POST_NOTIFICATIONS (рекомендуется API 33+)
