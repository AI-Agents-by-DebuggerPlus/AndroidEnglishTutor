# Тест кнопок Bluetooth-гарнитуры (AndEngTutor)

**Актуальная сборка приёмки:** 1.6.34 (versionCode 62)  
**Отображаемое имя:** AndEngTutor · пакет `com.englishtutor`  
**Устройства:** Pixel 6a + Pixel Buds Pro 2; дополнительно Grind (хуже стабильность серий)

---

## Актуальный гайд (2026-10-07)

**Полная инструкция по тестированию BT Play (1×/2×/3×/4×, 2Next, интервалы, ограничения Buds/Grind):**

→ [Reports/AndEngTutor_BT_Play_Testing_Guide_2026-10-07.md](Reports/AndEngTutor_BT_Play_Testing_Guide_2026-10-07.md)

Кратко по жестам на вкладке **Тесты → BT Play**:

| Серия Play | Счётчик |
|------------|---------|
| 1× | Play |
| 2× | Next |
| 3× | Stop |
| 4× | 4× (одна непрерывная серия; не «2+2») |
| два Next в окне 2Next | **2Next** (параллельный счётчик; не ломает Next) |

На BT Play окно серии **≥ 1.2 с** (после 3-го — до **1.8 с** под 4-е). HARDWARE **4×**: очень быстрый тап часто → Stop; пауза ~1 с между Play → 4× стабильнее. **2Next** срабатывает надёжно и может заменить 4×. Для проверки логики без железа: **Симулировать 4× Play**.
---

## Другие отчёты

- [Reports/AndEngTutor_Headset_Play_Gestures_Test_Report_2026-10-04.md](Reports/AndEngTutor_Headset_Play_Gestures_Test_Report_2026-10-04.md) — приёмка 1×/2×/3× (v1.6.18)
- [Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md](Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md) — проблемы, решения, порт
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
4. **AndEngTutor** → **Окно тестов** → вкладка **BT Play**.
5. **Reassert MediaSession** → cue *«BT test ready»* → `Native capture: ON`.
6. **Симулировать Play** → `[SIMULATED]`.
7. Одиночный / двойной / тройной Play на гарнитуре → Play / Next / Stop.
8. При «молчании» после YouTube → снова **Reassert**.

```bash
adb shell dumpsys media_session
# Ожидание: Media button session is com.englishtutor/.../AndEngTutorHeadset
```

---

## Архитектура (кратко)

```
Гарнитура (AVRCP / KeyEvent)
        │
        ▼
Android media-button session  ← UID com.englishtutor (claim + pulse)
        │
        ▼
HeadsetMonitorService (FGS mediaPlayback)
  • onMediaButtonEvent / onPlay / onPause / onSkipToNext / onSkipToPrevious
        │
        ▼
HeadsetButtonNotifier
  • HARDWARE vs SIMULATED
  • burst 1…4 (на вкладке BT Play), settle по интервалу серии
  • echo PLAY↔PAUSE
        │
        ├─ isolation ON (тесты) → HeadsetTestController (счётчики)
        └─ isolation OFF → EnglishTutorPlayHandler / WordStudyController
```

### Ключевые файлы

- `app/.../session/HeadsetMonitorService.kt`
- `app/.../session/HeadsetButtonNotifier.kt`
- `app/.../session/HeadsetTestController.kt`
- `app/.../session/HeadsetButtonPreferences.kt`
- `app/.../ui/screens/voicetest/VoiceTestScreen.kt`
