# AndroidEnglishTutor: Bluetooth-гарнитура — отчёт по реализации AndroidChat

**Версия отчёта:** 1.0  
**Дата:** 2026-08-28  
**Источник:** AndroidChat ≥ 1.0.46 (Kotlin / Compose)  
**Аудитория:** команда AndroidEnglishTutor — перенос или повторная реализация BT Play / STT / тестов кнопок

Связанные материалы в этом репо:

- [AndroidChat-STT-BT-Play-2026-07-26.md](AndroidChat-STT-BT-Play-2026-07-26.md)
- [Tasker-Headset-Testing.md](../Tasker-Headset-Testing.md)
- [AndroidChat-Headset-Profile-Sync-Test-Report-2026-07-09.md](AndroidChat-Headset-Profile-Sync-Test-Report-2026-07-09.md) (исторический Tasker-путь)

---

## 1. Краткое резюме

AndroidChat **не использует Tasker** для захвата кнопок гарнитуры (с v1.0.19). Вместо этого:

1. **Foreground-сервис** `HeadsetMonitorService` держит активный **MediaSessionCompat** и получает media-кнопки (Play, Pause, Next, HeadsetHook и т.д.).
2. **HeadsetButtonNotifier** классифицирует кнопки, debounce 500 ms, режим изоляции для тестов.
3. **HeadsetPlayHandler** реализует логику **BT Play**: пауза/возобновление TTS, STT, отправка `next` для EnglishLearning.
4. **Подключение гарнитуры** — ACL broadcast + запрос профилей HFP/A2DP по имени устройства.
5. **Тестирование** — вкладка **Tests → BT Play**: счётчик, журнал событий, Simulate Play.

Для AndroidEnglishTutor рекомендуется **скопировать пакет `headset/` + `HeadsetPlayHandler`** и адаптировать только прикладную логику Play (урок, карточки, навигация).

---

## 2. Архитектура

```
Bluetooth-гарнитура
        │ media keys (KeyEvent / MediaSession callback)
        ▼
HeadsetMonitorService          ← foreground + MediaSessionCompat
        │ notifyButton(label)
        ▼
HeadsetButtonNotifier          ← debounce, isolation, relay в чат
        │
        ├─ Play / Play-Pause / HEADSETHOOK ──► HeadsetPlayHandler
        │                                         ├─ TTS pause/resume (SpeechService)
        │                                         ├─ STT (VoiceInputService + SCO)
        │                                         └─ chat / next (ChatRepository)
        │
        └─ Next / Prev / Stop / … ──► ChatRepository (если enableHeadsetToChat)

Параллельно:
HeadsetConnectionMonitor → HeadsetConnectionReceiver (ACL CONNECT/DISCONNECT)
HeadsetConnectionHelper  → query HEADSET + A2DP, match по имени
```

| Слой | Классы | Назначение |
|------|--------|------------|
| Захват | `HeadsetMonitorService` | MediaSession, foreground notification |
| Маршрутизация | `HeadsetButtonNotifier`, `HeadsetButtonNames` | Метки кнопок, debounce, изоляция |
| Действие | `HeadsetPlayHandler` | State machine BT Play |
| Голос | `VoiceInputService`, `SpeechService` | STT + Bluetooth SCO, TTS pause/resume |
| Связь | `ChatRepository` | Supabase INSERT (текст, next, relay кнопок) |
| UI / тесты | `TestsScreen`, `MainViewModel` | Счётчик, журнал, isolation mode |
| Урок | `LessonHeadsetGuard`, `BluetoothToggleHelper` | Watchdog переподключения BT |

**Жизненный цикл:**

1. `AndroidChatApp.onCreate()` → `HeadsetConnectionMonitor.ensureStarted()` (ACL).
2. `MainViewModel.connect()` → после успешного Supabase → `updateHeadsetMonitor()`.
3. Если `enableNativeHeadsetCapture = true` → `HeadsetMonitorService.start()`.

---

## 3. Определение и мониторинг подключения гарнитуры

### 3.1 События ACL (connect / disconnect)

`HeadsetConnectionReceiver` слушает:

- `BluetoothDevice.ACTION_ACL_CONNECTED`
- `BluetoothDevice.ACTION_ACL_DISCONNECTED`

Регистрация **динамическая** (не в manifest — ограничение Android 8+).  
Старт через `HeadsetConnectionMonitor.ensureStarted()` из:

- `AndroidChatApp.onCreate()`
- `HeadsetMonitorService.onCreate()`

При совпадении имени устройства с подсказкой (по умолчанию `"Pixel Buds Pro 2"`) `SpeechService.speakRussian()` озвучивает, например: *«Pixel Buds Pro 2 подключены»*.

### 3.2 Запрос «подключена ли гарнитура сейчас»

`HeadsetConnectionHelper.isTargetHeadsetConnected()`:

- Профили: `BluetoothProfile.HEADSET` (HFP), `BluetoothProfile.A2DP`
- `BluetoothManager.getConnectedDevices(profile)`
- Сравнение `device.name` с подстрокой из настроек (`lessonHeadsetDeviceName`)

Требует **`BLUETOOTH_CONNECT`** (API 31+). Без разрешения возвращает `false` и пишет warning в logcat.

### 3.3 Режим урока — принудительное переподключение

`LessonHeadsetGuard` (опрос каждые 30 с): если целевая гарнитура не найдена → `BluetoothToggleHelper.cycleBluetooth()` (выкл → 1.5 с → вкл). На Pixel/Android 13+ прямой `adapter.disable()` часто не срабатывает — cycle используется как обходной путь.

---

## 4. Захват кнопок управления

### 4.1 HeadsetMonitorService

Файл: `AndroidChat/.../headset/HeadsetMonitorService.kt`

- Тип foreground: `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
- `MediaSessionCompat("AndroidChatHeadset")` с флагами:
  - `FLAG_HANDLES_MEDIA_BUTTONS`
  - `FLAG_HANDLES_TRANSPORT_CONTROLS`
- Callback:
  - `onPlay()` → `"MEDIA_PLAY"`
  - `onPause()` → `"MEDIA_PAUSE"`
  - `onSkipToNext()` / `onSkipToPrevious()` / `onStop()`
  - `onMediaButtonEvent()` → разбор `Intent.EXTRA_KEY_EVENT`, только `ACTION_DOWN` и `repeatCount == 0`

Зависимость: `androidx.media:media:1.7.0`.

### 4.2 Маппинг KeyEvent → метка

`HeadsetButtonNames.fromKeyCode()`:

| KeyEvent | Метка |
|----------|-------|
| `KEYCODE_HEADSETHOOK` | `HEADSETHOOK` |
| `KEYCODE_MEDIA_PLAY` | `MEDIA_PLAY` |
| `KEYCODE_MEDIA_PLAY_PAUSE` | `MEDIA_PLAY_PAUSE` |
| `KEYCODE_MEDIA_PAUSE` | `MEDIA_PAUSE` |
| `KEYCODE_MEDIA_NEXT` | `MEDIA_NEXT` |
| `KEYCODE_MEDIA_PREVIOUS` | `MEDIA_PREVIOUS` |
| `KEYCODE_MEDIA_STOP` | `MEDIA_STOP` |
| `KEYCODE_MEDIA_FAST_FORWARD` | `MEDIA_FAST_FORWARD` |
| `KEYCODE_MEDIA_REWIND` | `MEDIA_REWIND` |
| `KEYCODE_VOICE_ASSIST` | `VOICE_ASSIST` |

### 4.3 HeadsetButtonNotifier — маршрутизация

**BT Play** (метки `MEDIA_PLAY`, `MEDIA_PLAY_PAUSE`, `HEADSETHOOK`, `PLAY`):

- Debounce **500 ms** на ключ BT Play
- **Режим изоляции** (`btPlayTestIsolation`): только счётчик UI / локальный handler, без STT и чата
- **Обычный режим** → `HeadsetPlayHandler.handleBtPlay()`

**Остальные кнопки** (Next, Prev, Stop…):

- Если `enableHeadsetToChat = true` → INSERT в Supabase: `🎧 [native] MEDIA_NEXT` и т.п.
- В режиме изоляции — игнорируются

---

## 5. Логика BT Play (HeadsetPlayHandler)

Файл: `AndroidChat/.../bridge/HeadsetPlayHandler.kt`

| Состояние приложения | Действие при BT Play |
|----------------------|----------------------|
| STT уже слушает | `cancel()` распознавания |
| TTS контента на паузе | cue «Continue» → resume |
| TTS контента играет | pause + cue «Pause» |
| Idle | `VoiceInputService.startListening()` → текст в чат |
| STT пусто / ошибка / cancel + `recipientName = EnglishLearning` | cue «Play» → отправить **`next`** |

**Bluetooth SCO для микрофона** (`VoiceInputService`):

```text
AudioManager.MODE_IN_COMMUNICATION
startBluetoothSco() / isBluetoothScoOn = true
→ SpeechRecognizer
→ stopBluetoothSco() после завершения
```

---

## 6. Тестирование кнопок гарнитуры

### 6.1 UI: Tests → вкладка BT Play

Файл: `AndroidChat/.../ui/TestsScreen.kt` → `BtPlayTestSection`

| Элемент | Назначение |
|---------|------------|
| Статус capture | ON/OFF (`enableNativeHeadsetCapture`) |
| Большой счётчик | Число нажатий Play |
| Last event | Метка + время последнего события |
| **Simulate Play** | `notifyButton("MEDIA_PLAY", source="ui-simulate")` без гарнитуры |
| Журнал | До 40 строк событий |

Открытие: меню → **Tests** (или из настроек).

### 6.2 Режим изоляции

`MainViewModel.enterHeadsetIsolation()`:

- `btPlayTestIsolation = true`
- Запуск `HeadsetMonitorService` при включённом native capture
- BT Play **не** вызывает STT / pause / next / relay в чат
- На вкладке **STT** можно задать `isolatedBtPlayHandler` для локального теста распознавания

`exitHeadsetIsolation()` — сброс при выходе из Tests / Lesson.

### 6.3 Пошаговый сценарий теста (реальная гарнитура)

1. Подключить гарнитуру по Bluetooth.
2. AndroidChat → **Tests** → вкладка **BT Play**.
3. В настройках включить **«Захват Play на гарнитуре»** (`enableNativeHeadsetCapture`).
4. Выдать **RECORD_AUDIO** (запрос при старте приложения).
5. На Android 12+ вручную выдать **Nearby devices / Bluetooth** (`BLUETOOTH_CONNECT`) — **в коде нет runtime-запроса**, без этого ACL/профили могут молчать.
6. Нажать **Play** на гарнитуре → счётчик +1, в журнале метка (`MEDIA_PLAY`, `HEADSETHOOK` и т.д.).
7. Опционально: **Simulate Play** — проверка цепочки без BT.
8. Для полного цикла STT: выйти из isolation (обычный чат) или вкладка STT с handler.

### 6.4 Логи для отладки

Локальный буфер AndroidChat, категории:

- `Headset` — capture, isolation, маршрутизация
- `Voice` — STT результат
- Отправка на WpfChat: `[LOG:Headset] …` через Supabase (см. протокол логов)

---

## 7. Настройки (AppSettings)

| Поле | По умолчанию | Описание |
|------|--------------|----------|
| `enableNativeHeadsetCapture` | `true` | Запуск `HeadsetMonitorService` |
| `enableHeadsetToChat` | `true` | Next/Prev/Stop → сообщение в чат |
| `voiceInputLocale` | `"ru-RU"` | Локаль STT после BT Play |
| `lessonHeadsetDeviceName` | `"Pixel Buds Pro 2"` | Подстрока имени BT-устройства |
| `recipientName` | `"WpfChat"` | Куда уходит STT / next |

UI (Settings в `MainScreen.kt`):

- «Кнопки гарнитуры (кроме Play) → чат»
- «Захват Play на гарнитуре (BT Play → STT / pause / next)»
- Поле имени гарнитуры (режим урока)

Миграция: `SettingsRepository.migrateNativeBtPlayDefaults()` принудительно включает native capture один раз (v1.0.19+).

---

## 8. Permissions и manifest

`AndroidChat/app/src/main/AndroidManifest.xml`:

```xml
android.permission.MODIFY_AUDIO_SETTINGS
android.permission.RECORD_AUDIO
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK
android.permission.POST_NOTIFICATIONS
android.permission.BLUETOOTH          (maxSdkVersion=30)
android.permission.BLUETOOTH_CONNECT
```

Сервис:

```xml
<service android:name=".headset.HeadsetMonitorService"
         android:exported="false"
         android:foregroundServiceType="mediaPlayback" />
```

**Пробел:** `BLUETOOTH_CONNECT` объявлен, но **runtime-запрос не реализован** — на API 31+ пользователь должен выдать вручную в системных настройках.

---

## 9. Tasker vs native (история)

| Период | Подход |
|--------|--------|
| До v1.0.19 | Tasker-профиль **BT Headset Play Pause** → Broadcast → AndroidChat |
| v1.0.19+ | **Native MediaSession** в приложении; Tasker для Play **не нужен** |

В **текущем коде** нет:

- `TaskerHeadsetProfileHelper`
- Broadcast `HEADSET_PROFILE_ON/OFF`
- Настройки `enableHeadsetProfileSync`

Документы и XML в `Docs/TaskerTasks/` — **архив** для отладки и сравнения. Политика: выключить профиль **BT Key** в Tasker, когда не тестируете Tasker ([Tasker-BT-Key-Policy.md](Tasker-BT-Key-Policy.md)).

---

## 10. Рекомендации для AndroidEnglishTutor

### 10.1 Минимальный перенос (MVP)

1. Скопировать пакет `com...headset.*` (7–8 файлов).
2. Скопировать `HeadsetPlayHandler` → переименовать в `EnglishTutorPlayHandler`.
3. Зарегистрировать сервис в manifest + permissions.
4. Добавить вкладку **BT Play** в экран тестов (можно упростить UI).
5. В `Application.onCreate()` — `HeadsetConnectionMonitor.ensureStarted()`.

### 10.2 Адаптация логики Play

Заменить ветки `HeadsetPlayHandler`:

| AndroidChat | AndroidEnglishTutor (предложение) |
|-------------|-----------------------------------|
| STT → Supabase chat | STT → поле ответа / проверка произношения |
| TTS pause/resume контента | pause/resume карточки урока |
| `next` при EnglishLearning | `next card` / `skip` / `repeat` |
| Relay Next/Prev в чат | локальная навигация или Hermes |

### 10.3 Обязательные доработки при переносе

- **Runtime-запрос `BLUETOOTH_CONNECT`** на API 31+ (Activity Result API).
- Единый **`sender_name = AndroidEnglishTutor`** для логов `[LOG:Headset]`.
- Foreground notification с понятным текстом (требование Android 14+).
- Тест **Simulate Play** в CI/emulator без физической гарнитуры.

### 10.4 Чеклист приёмки BT в AndroidEnglishTutor

- [ ] Play на гарнитуре увеличивает счётчик на Tests → BT Play
- [ ] В журнале видна метка (`MEDIA_PLAY` / `HEADSETHOOK`)
- [ ] Simulate Play работает без BT
- [ ] При озвучке урока Play ставит на паузу / снимает с паузы
- [ ] В idle Play запускает STT с микрофона гарнитуры (SCO)
- [ ] ACL connect/disconnect озвучивается или пишется в лог
- [ ] `BLUETOOTH_CONNECT` запрошен и выдан
- [ ] Tasker BT Key **выключен** на время теста (не перехватывает Play)
- [ ] `adb shell dumpsys media_session` в момент нажатия Play показывает `com.englishtutor` как единственную/верхнюю активную сессию, а не только `HeadsetMonitorService` в списке процессов

---

## 11. Ключевые файлы AndroidChat

| Путь | Роль |
|------|------|
| `headset/HeadsetMonitorService.kt` | Foreground + MediaSession |
| `headset/HeadsetButtonNotifier.kt` | Debounce, routing, isolation |
| `headset/HeadsetButtonNames.kt` | KeyEvent → метка |
| `bridge/HeadsetPlayHandler.kt` | BT Play state machine |
| `headset/HeadsetConnectionMonitor.kt` | ACL receiver lifecycle |
| `headset/HeadsetConnectionReceiver.kt` | CONNECT/DISCONNECT → TTS |
| `headset/HeadsetConnectionHelper.kt` | HFP/A2DP query |
| `headset/HeadsetConnectionConstants.kt` | Default device hint |
| `headset/BluetoothPermissionHelper.kt` | Проверка BLUETOOTH_CONNECT |
| `headset/BluetoothToggleHelper.kt` | BT cycle (урок) |
| `headset/LessonHeadsetGuard.kt` | Watchdog переподключения |
| `voice/VoiceInputService.kt` | STT + Bluetooth SCO |
| `SpeechService.kt` | TTS + content pause/resume |
| `MainViewModel.kt` | Service lifecycle, isolation, counters |
| `ui/TestsScreen.kt` | UI тестов BT Play |
| `data/Models.kt` | AppSettings headset fields |
| `AndroidManifest.xml` | Permissions + service |

---

## 12. Схема потока BT Play (production)

```
[Play на гарнитуре]
        │
        ▼
HeadsetMonitorService.onPlay / onMediaButtonEvent
        │
        ▼
HeadsetButtonNotifier (debounce 500ms)
        │
        ├─ isolation? → счётчик Tests / local handler → STOP
        │
        ▼
HeadsetPlayHandler.handleBtPlay()
        │
        ├─ listening? → cancel STT
        ├─ TTS paused? → cue Continue → resume
        ├─ TTS playing? → pause + cue Pause
        └─ idle → VoiceInputService (SCO) → текст → Supabase
                      └─ empty + EnglishLearning → next
```

---

*Отчёт подготовлен по исходникам TaskerToWpf / AndroidChat. Для вопросов по Tasker-legacy см. `Docs/TaskerTasks/` и MD_Files с префиксом Tasker-/AndroidChat-Headset-*.*
