# Отчёт: BT Play Test в AndroidEnglishTutor (AndEngTutor)

**Дата:** 2026-09-06  
**Сборка:** v1.5.0 (21) · debug  
**Пакет:** `com.englishtutor` (отображаемое имя: **AndEngTutor**)  
**Источник задания:**  
`D:\Programming\Cursor\2026\September\BT_Play\Docs\Tasks\Cursor\AndroidEnglishTutor_BT_Play_Test_Agent_Instruction.md`  
**Эталон:** BT_Play / `AndroidChatBtTest95`

---

## 1. Цель задания

Добавить в AndroidEnglishTutor изолированный экран тестирования аппаратной кнопки BT Play (счётчик, журнал HARDWARE/SIMULATED, Reassert MediaSession) по рабочему контуру из BT_Play, не ломая обычный урок/lesson flow.

Дополнительно по запросу пользователя:

- переименовать приложение в **AndEngTutor**;
- иконка с буквой **E** для быстрого поиска на лаунчере.

---

## 2. Что реализовано из инструкции

### 2.1. Критерии готовности (§6 инструкции)

| Критерий | Статус | Комментарий |
|---|---|---|
| FGS `mediaPlayback` + активная MediaSession | ✅ | `HeadsetMonitorService` |
| Аппаратный Play → UI-счётчик (HARDWARE) без конкурентов | ✅ | Подтверждено логом 2026-09-06 14:04 (v1.5.1) |
| Simulate Play (SIMULATED) | ✅ | Подтверждено логом |
| Isolation на тестовом экране | ✅ | `btPlayTestIsolation`; вне теста — lesson handler |
| Reassert после конкурента | ✅ | Кнопка + `HeadsetMonitorService.reassert()` |
| Нет краша reattach с фонового потока | ✅ | Attach через main `Handler` / main thread |
| Ссылка на эталон BT_Play | ✅ | Этот отчёт + комментарии в `HeadsetMonitorService` |

### 2.2. Ядро BT Play (§3.1 / §4)

| Требование инструкции | Реализация в Tutor |
|---|---|
| FGS + MediaSessionCompat | `session/HeadsetMonitorService.kt` |
| `requestAudioFocus()` до сессии | USAGE_MEDIA + AUDIOFOCUS_GAIN |
| Трюк PLAYING → PAUSED | claim media-button session (как BtTest95) |
| `reassert()` / `EXTRA_FORCE_REASSERT` | force reattach MediaSession |
| Callbacks на main thread | `attachMediaSessionOnMain()` |
| ACL → force reattach | `BluetoothConnectionMonitor.setOnAclEventListener` |
| Имена кнопок | `HeadsetButtonNames.kt` |
| Debounce + isolation + HARDWARE/SIMULATED | `HeadsetButtonNotifier.kt` |
| UI: счётчик, журнал, capture ON/OFF, Simulate, Reassert, сброс | `VoiceTestScreen` / вкладка BT Play |
| При открытии BT-вкладки — reassert + cue | `reassertBtPlayCapture()` + TTS *«BT test ready»* |
| ACL monitor при старте | `EnglishTutorApp` / `BluetoothConnectionMonitor.ensureStarted` |
| Manifest FGS mediaPlayback | уже был; сохранён |

### 2.3. UI / продукт (вне §3, по запросу)

- Имя приложения: **AndEngTutor** (`strings.xml` → `app_name`).
- Иконка: векторный foreground с буквой **E**.
- Диагностика разрешений / media-кнопок (карточка внизу экрана тестов).
- Предупреждение о возможном конфликте Tasker (`SET_MEDIA_KEY_LISTENER`).

### 2.4. Что из инструкции сознательно не тащили

- Полный SpeechService / USAGE_MEDIA MediaPlayer из CopyV1 (достаточно reassert + AudioFocus + TTS cue).
- ActiveSessions / NoOpNotificationListener / Intercept monitor из BtTest93 (диагностика конкурентов — через `dumpsys` и UI-диагностику).
- A/B Baseline toggles из BtTest93 — поведение «как 95» всегда ON.

---

## 3. Архитектура текущего контура

```
Гарнитура (AVRCP Play)
    → Android media-button session
        → HeadsetMonitorService (FGS + MediaSession + AudioFocus)
            → HeadsetButtonNotifier (debounce, HARDWARE/SIMULATED)
                ├─ isolation ON (экран тестов) → счётчик / журнал UI
                └─ isolation OFF → EnglishTutorPlayHandler → урок
```

Simulate Play идёт напрямую в `HeadsetButtonNotifier` с `source=ui-simulate` → метка **SIMULATED**.

---

## 4. Доказательства из логов (2026-09-06)

Файл: `Files/Logs/androidenglishtutor.log`

### Сработало

- Старт v1.5.0, AudioFocus `result=1`, MediaSession `attached force=true`.
- ACL: *Pixel Buds Pro 2 connected*.
- Открытие Tests: isolation ON, reassert, cue *«BT test ready»*.
- Диагностика: *«Готов к приёму media-кнопок»*.
- Simulate:

```text
BT button Play via ui-simulate (SIMULATED)
SIMULATED: MEDIA_PLAY via ui-simulate → isolation (counter only)
```

### Не сработало / не зафиксировано

- Нет строк вида `HARDWARE` / `hardware-callback-onPlay` / `hardware-mediaButtonEvent`.
- Пользователь нажимал Play на гарнитуре в тестере — **реакции в логе и (по словам пользователя) в UI не было**.

### Сопутствующие WARN

- В начале сессии: `BLUETOOTH_CONNECT not granted` (до выдачи прав).
- `POST_NOTIFICATIONS` не выдано.
- Active device иногда: `Pixel 6a · COMMUNICATION` при подключённых Buds.

---

## 5. Текущая проблема (резюме)

### Симптом

На вкладке BT Play:

- **Simulate** → счётчик растёт, в логе SIMULATED;
- **физический Play** на Pixel Buds Pro 2 → нет HARDWARE, счётчик не растёт.

### Вывод

Цепочка **внутри приложения после события** исправна (notifier, isolation, UI).  
Событие **не приходит** от системы в `MediaSession.Callback`.

Это типичный случай из инструкции BtTest95 / §1 «Главный урок»:

> Android отдаёт AVRCP Play/Pause той MediaSession, которая сейчас **media-button session**.  
> Простого `isActive` + PAUSED недостаточно, если сверху YouTube / другое media-приложение.

### Наиболее вероятные внешние причины

1. **Конкурирующая media-button session** (YouTube, музыкальный плеер, другое BT-приложение).
2. **Tasker Grab** (профиль BT Key), даже если в конкретном дампе диагностики WARN не попал в выгрузку.
3. Реже: маршрут аудио на динамик телефона (`active=Pixel 6a`), не как первопричина AVRCP, но стоит учитывать.

### Что это **не** похоже

- Не баг счётчика / isolation (Simulate OK).
- Не «сервис не стартовал» (MediaSession + AudioFocus в логе).
- Не «гарнитура не подключена» (ACL + buds в connected).

---

## 6. Рекомендуемые следующие шаги

1. Перед тестом:
   ```bash
   adb shell am force-stop com.google.android.youtube
   ```
   Закрыть прочие media-приложения; при необходимости отключить Tasker BT Key / Grab.
2. AndEngTutor → Окно тестов → BT Play → **Reassert MediaSession** → Play на гарнитуре.
3. В момент нажатия:
   ```bash
   adb shell dumpsys media_session
   ```
   Проверить **Media button session** / package: должен быть `com.englishtutor` после reassert/cue.
4. Ожидаемый лог при успехе:
   ```text
   BT button Play via hardware-callback-onPlay (HARDWARE)
   HARDWARE: MEDIA_PLAY via hardware-... → isolation (counter only)
   ```
5. Если после force-stop конкурентов и Reassert HARDWARE всё ещё нет — следующий раунд: ActiveSessions-диагностика (эталон BtTest93) или сравнение `dumpsys` с рабочим AndroidChatBtTest95 на том же устройстве.

---

## 7. Ключевые файлы Tutor

| Файл | Роль |
|---|---|
| `app/.../session/HeadsetMonitorService.kt` | FGS, AudioFocus, PLAYING→PAUSED, reassert |
| `app/.../session/HeadsetButtonNotifier.kt` | Debounce, isolation, HARDWARE/SIMULATED |
| `app/.../session/HeadsetTestController.kt` | Счётчик и журнал UI |
| `app/.../ui/screens/voicetest/VoiceTestViewModel.kt` | Isolation, reassert + cue |
| `app/.../ui/screens/voicetest/VoiceTestScreen.kt` | Вкладка BT Play, Simulate, Reassert |
| `app/.../bluetooth/BluetoothConnectionMonitor.kt` | ACL + listener для reattach |
| `app/src/main/res/values/strings.xml` | `app_name` = AndEngTutor |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Иконка с буквой E |

---

## 8. Итог одной фразой

**Реализован контур BT Play Test по BtTest95; в v1.5.1 добавлен USAGE_MEDIA pulse — физический Play на Pixel Buds Pro 2 даёт HARDWARE. Приложение: AndEngTutor (иконка E).**

---

## 9. Обновление после v1.5.1 (HARDWARE подтверждён)

Из `Files/Logs/androidenglishtutor.log` (14:04):

```text
MediaPulse: USAGE_MEDIA pulse done (180ms)
HeadsetMonitor: MediaSession claim refreshed after USAGE_MEDIA pulse
Headset: BT button Play via hardware-mediaButtonEvent (HARDWARE)
HARDWARE: MEDIA_PLAY via hardware-mediaButtonEvent → isolation (counter only)
```

Множественные HARDWARE-события подряд. Follow-up: [AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md](./AndEngTutor_BT_Play_Hardware_Fix_Notes_2026-09-06.md).
