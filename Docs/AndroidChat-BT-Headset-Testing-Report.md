# Отчёт: тестирование кнопок Bluetooth-гарнитуры в AndroidChat

**Дата:** 2026-08-28  
**Источник:** `D:\Programming\Cursor\2026\Connectors\TaskerToWpf\AndroidChat`  
**Версия приложения:** 1.0.46 (versionCode 47)

---

## 1. Краткое резюме

В AndroidChat тестирование кнопок гарнитуры реализовано через **экран «Тесты»** (вкладка **BT Play**) и опирается на **нативный захват media-кнопок** через foreground-сервис `HeadsetMonitorService` + `MediaSessionCompat`.

Основной сценарий с версии **1.0.19**: приложение **само** перехватывает Play/Pause/Next/Previous с гарнитуры, **без Tasker**. На вкладке BT Play включён **режим изоляции** — кнопки только считаются и логируются, без запуска STT/TTS/отправки в чат.

Старый путь через Tasker Broadcast `HEADSET_BUTTON` описан в документации проекта, но **в текущем коде receiver для него отсутствует** (удалён в пользу native capture).

---

## 2. Архитектура

```
Bluetooth-гарнитура
        │
        ▼
HeadsetMonitorService (Foreground + MediaSessionCompat)
        │  onPlay / onPause / onSkipToNext / onMediaButtonEvent
        ▼
HeadsetButtonNotifier.notifyButton(label, source)
        │
        ├─► BT Play? (MEDIA_PLAY, MEDIA_PLAY_PAUSE, HEADSETHOOK, PLAY)
        │       │
        │       ├─► onBtPlayDetected → MainViewModel.recordBtPlayEvent() → UI счётчик
        │       │
        │       ├─► btPlayTestIsolation == true?
        │       │       ├─ Да + isolatedBtPlayHandler → локальная реакция (STT-вкладка / урок)
        │       │       └─ Да, handler=null → только счётчик (вкладка BT Play)
        │       │
        │       └─ Нет → HeadsetPlayHandler.handleBtPlay() → STT / pause TTS / next
        │
        └─► Другие кнопки (Next, Pause, …)
                └─► enableHeadsetToChat? → сообщение «🎧 [native] LABEL» в Supabase/WpfChat
```

### Ключевые классы

| Класс | Роль |
|-------|------|
| `HeadsetMonitorService` | Foreground-сервис, держит `MediaSessionCompat`, перехватывает media-кнопки |
| `HeadsetButtonNames` | Маппинг `KeyEvent` → строковые метки (`MEDIA_PLAY`, `HEADSETHOOK`, …) |
| `HeadsetButtonNotifier` | Центральный диспетчер: debounce, изоляция, маршрутизация Play vs остальные |
| `HeadsetPlayHandler` | Боевая логика BT Play: pause/resume TTS, STT, отправка в чат |
| `MainViewModel` | Состояние UI тестов, изоляция, счётчик, симуляция |
| `TestsScreen` | UI: вкладки TTS / STT / BT Play |
| `HeadsetConnectionMonitor` | Мониторинг подключения/отключения BT-гарнитуры (ACL), не кнопки |
| `LessonHeadsetGuard` | В режиме урока — переподключение гарнитуры при обрыве BT |

---

## 3. Как открыть экран тестирования

1. **Настройки** (⚙) → кнопка **«Открыть тесты»** (`settings_open_tests`).
2. Экран `TestsScreen` с тремя вкладками:
   - **TTS** — выбор и прослушивание голосов
   - **STT** — тест SpeechRecognizer
   - **BT Play** — тест кнопок гарнитуры

При открытии тестов вызывается `MainViewModel.openTests()`:
- `enterHeadsetIsolation()` — включает `btPlayTestIsolation = true`
- при `enableNativeHeadsetCapture = true` стартует `HeadsetMonitorService`
- по умолчанию открывается вкладка **STT** (index 1), для BT Play нужно переключиться на вкладку 3

---

## 4. Вкладка «BT Play» — что показывает UI

Файл: `ui/TestsScreen.kt`, секция `BtPlayTestSection`.

| Элемент | Описание |
|---------|----------|
| Подсказка | «Нажмите Play на гарнитуре. Счётчик растёт… TTS/STT/next отключены» |
| Статус capture | `Native capture: ON (MediaSession)` или `OFF — включите в настройках` |
| Большой счётчик | Число нажатий BT Play |
| Последнее событие | Метка кнопки + время (`HH:mm:ss.SSS`) |
| Кнопка «Симулировать Play» | Вызывает `notifyButton("MEDIA_PLAY", source="ui-simulate")` без гарнитуры |
| Журнал событий | До 40 строк вида `12:34:56.789  MEDIA_PLAY  (#3)` |
| Сброс (↻ в toolbar) | `resetBtPlayCounter()` — обнуляет счётчик и журнал |

---

## 5. Режим изоляции (`btPlayTestIsolation`)

При открытии экрана тестов или генератора урока:

```kotlin
// MainViewModel.enterHeadsetIsolation()
app.headsetButtonNotifier.btPlayTestIsolation = true
if (settings.enableNativeHeadsetCapture) {
    HeadsetMonitorService.start(context)
}
```

Поведение Play в изоляции (`HeadsetButtonNotifier`):

| Контекст | Реакция на Play |
|----------|-----------------|
| Вкладка **BT Play** (tab 2) | Только счётчик + запись в журнал. STT/TTS/chat **не** вызываются |
| Вкладка **STT** (tab 1) | Play **переключает** STT: start ↔ cancel (`isolatedBtPlayHandler`) |
| Экран **урока** | Play управляет фазой урока: тема, confirm, pause/resume TTS, навигация |
| Любой другой экран | Изоляция выключена → обычная логика `HeadsetPlayHandler` |

При закрытии тестов/урока: `exitHeadsetIsolation()` сбрасывает флаги.

---

## 6. Нативный захват кнопок (`HeadsetMonitorService`)

### Как работает

1. Стартует как **foreground service** с типом `mediaPlayback` (Android Q+).
2. Создаёт `MediaSessionCompat("AndroidChatHeadset")` с флагами:
   - `FLAG_HANDLES_MEDIA_BUTTONS`
   - `FLAG_HANDLES_TRANSPORT_CONTROLS`
3. Регистрирует `MediaSessionCompat.Callback`:
   - `onPlay()` → `MEDIA_PLAY`
   - `onPause()` → `MEDIA_PAUSE`
   - `onSkipToNext()` → `MEDIA_NEXT`
   - `onSkipToPrevious()` → `MEDIA_PREVIOUS`
   - `onStop()` → `MEDIA_STOP`
   - `onMediaButtonEvent()` → разбор `KeyEvent` через `HeadsetButtonNames.fromKeyCode()`
4. Устанавливает `PlaybackStateCompat.STATE_PAUSED` с actions PLAY/PAUSE/SKIP/STOP.
5. Показывает постоянное уведомление «BT Play (AndroidChat)».

### Когда сервис запускается

- При `connect()` если `enableNativeHeadsetCapture = true` (`updateHeadsetMonitor()`)
- При сохранении настроек с включённым capture
- При входе в изоляцию (тесты/урок), если capture включён

### Debounce

`HeadsetButtonNotifier` отбрасывает повторные события одной кнопки в течение **500 мс**.

---

## 7. Боевая логика BT Play (вне тестов)

Класс `HeadsetPlayHandler` — срабатывает когда `btPlayTestIsolation = false`:

| Состояние приложения | Действие при Play |
|---------------------|-------------------|
| STT уже слушает | Cancel распознавания |
| TTS контента на паузе | Cue «Continue» → resume |
| TTS контента играет | Pause + cue «Pause» |
| Idle | Запуск SpeechRecognizer → текст в чат (Supabase) |
| Пустой/ошибка STT + recipient=`EnglishLearning` | Cue «Play» → отправка `next` |

Небоевые кнопки (Next, Previous, …) при `enableHeadsetToChat = true` уходят в чат как `🎧 [native] MEDIA_NEXT`.

---

## 8. Настройки

| Настройка | По умолчанию | Назначение |
|-----------|--------------|------------|
| `enableNativeHeadsetCapture` | `true` (миграция в SettingsRepository) | Вкл/выкл `HeadsetMonitorService` |
| `enableHeadsetToChat` | `true` | Не-Play кнопки → сообщение в WpfChat |
| `voiceInputLocale` | `ru-RU` | Locale для STT при BT Play |
| `lessonHeadsetDeviceName` | `Pixel Buds Pro 2` | Подстрока имени гарнитуры для урока |

В UI настроек:
- «Кнопки гарнитуры (кроме Play) → чат»
- «Захват Play на гарнитуре (BT Play → STT / pause / next)»

---

## 9. Путь через Tasker (legacy)

Документация (`Docs/Tasker-Headset-Testing.md`, `Docs/AndroidChat.md`) описывает цепочку:

```
Гарнитура → Tasker (Media Button profile) → Broadcast HEADSET_BUTTON → AndroidChat → WpfChat
```

Параметры Broadcast:
- Action: `com.taskertowpf.androidchat.HEADSET_BUTTON`
- Package: `com.taskertowpf.androidchat`
- Extra: `button` = `Play` / `Pause` / `Next` / …

**Важно:** в текущей версии кода (1.0.46):
- В `AndroidManifest.xml` **нет** `BroadcastReceiver` для `HEADSET_BUTTON`
- Папка `bridge/` содержит только `HeadsetPlayHandler.kt` (ранее были `TaskerBridge`, `TaskerChatReceiver` — удалены)
- Рекомендуемый путь — **native capture**, Tasker-профили BT Headset Play Pause нужно **выключить** (иначе конфликт MediaSession)

Актуальная документация: `Docs/MD_Files/AndroidChat-STT-BT-Play-2026-07-26.md`.

---

## 10. Симуляция и отладка без гарнитуры

1. **Кнопка «Симулировать Play»** на вкладке BT Play.
2. **Журнал событий** на экране тестов.
3. **Экран «Логи»** (📄) — категория `Headset`, записи вида:
   - `BT Play (MEDIA_PLAY) via native → isolation (только счётчик)`
   - `BT Play (MEDIA_PLAY) via ui-simulate → isolated handler`
4. **Logcat** теги: `HeadsetButtonNotifier`, `HeadsetMonitorService`, `HeadsetPlayHandler`.

---

## 11. Известные ограничения и конфликты

1. **Конфликт с Tasker:** если одновременно включены Tasker Media Button profile и `HeadsetMonitorService`, Android отдаёт кнопку только одному «слушателю». См. `Docs/Known-Issues.md`.
2. **Документация отстаёт от кода:** `AndroidChat.md` всё ещё ссылается на `TaskerChatReceiver`, `HEADSET_BUTTON` — в APK этого уже нет.
3. **Только BT Play тестируется изолированно:** вкладка BT Play считает именно Play-эквиваленты; Next/Previous в изоляции игнорируются.
4. **Foreground notification обязателен** для удержания MediaSession в фоне.

---

## 12. Сравнение с AndroidEnglishTutor (v1.3.3)

| Аспект | AndroidChat | AndroidEnglishTutor |
|--------|-------------|---------------------|
| Сервис теста | `HeadsetMonitorService` | `HeadsetTestService` |
| Сервис урока | — | `LessonSessionService` |
| UI тестов | `TestsScreen` — вкладки TTS/STT/BT Play | `VoiceTestScreen` — те же 3 вкладки |
| Автозапуск capture | При открытии тестов / вкладки BT | При выборе вкладки BT Play |
| Счётчик / журнал | `btPlayPressCount`, `btPlayEventLog` | `HeadsetTestController.pressCount`, `eventLog` |
| Симуляция Play | `simulateBtPlay()` | `simulateBtPlay()` |
| Debounce | 500 мс | 500 мс |
| Audio focus в тесте | Нет | Нет (с v1.3.3) |
| `onMediaButtonEvent` | Да | Да (с v1.3.2) |
| TTS при нажатии в тесте | Нет | Нет (с v1.3.3) |
| Tasker relay | Устарел | Не реализован |

Документация EnglishTutor: [headset-testing.md](headset-testing.md)

---

## 13. Файлы для изучения

```
AndroidChat/app/src/main/java/com/taskertowpf/androidchat/
├── headset/
│   ├── HeadsetMonitorService.kt      # MediaSession + foreground
│   ├── HeadsetButtonNotifier.kt      # Диспетчер событий
│   ├── HeadsetButtonNames.kt         # KeyEvent → label
│   ├── HeadsetConnectionMonitor.kt   # ACL connect/disconnect
│   └── LessonHeadsetGuard.kt         # Переподключение BT в уроке
├── bridge/
│   └── HeadsetPlayHandler.kt         # Боевая логика BT Play
├── ui/
│   └── TestsScreen.kt                # UI тестов (вкладка BT Play)
├── MainViewModel.kt                  # Изоляция, счётчик, openTests()
└── data/Models.kt                    # enableNativeHeadsetCapture, enableHeadsetToChat

Docs (TaskerToWpf):
├── Tasker-Headset-Testing.md         # Legacy Tasker path
├── MD_Files/AndroidChat-STT-BT-Play-2026-07-26.md
└── Known-Issues.md                   # Конфликты MediaSession
```

---

## 14. Чеклист ручного теста BT Play

1. В настройках AndroidChat: **«Захват Play на гарнитуре»** = ON.
2. Подключить BT-гарнитуру, убедиться что видно уведомление «BT Play (AndroidChat)».
3. **Настройки → Открыть тесты → вкладка BT Play**.
4. Нажать Play на гарнитуре → счётчик +1, запись в журнале.
5. Нажать «Симулировать Play» → счётчик +1, source `ui-simulate`.
6. Открыть **Логи** → записи категории `Headset`.
7. Закрыть тесты → Play снова запускает STT (если не на паузе TTS).
8. При конфликте: выключить Tasker-профиль **BT Headset Play Pause**.

---

## 15. Результаты теста AndroidEnglishTutor (2026-08-28)

**Скриншот:** `Files/Images/Screenshot_20260828_112048.png`  
**Сборка:** v1.3.1 (7) debug  
**Логи:** экран «Логи» + `Files/Logs/androidenglishtutor.log`

### Что видно на скриншоте

| Время | Событие | Вывод |
|-------|---------|-------|
| 11:19:58 | `[VoiceTest]` Screen opened, mic OK | Экран тестов открыт нормально |
| 11:20:05 | `[HeadsetTest]` Headset test ACTIVE | Сервис `HeadsetTestService` запущен |
| 11:20:23 | `[HeadsetTest]` STOPPED (×2) | Тест остановлен (дубль — стоп + выход с экрана) |
| 11:20:30–31 | `[Supabase]` INSERT failed id=2…6 | **Все 5 записей не ушли на сервер** |

### Проблема 1: кнопки гарнитуры не залогированы

Между ACTIVE (11:20:05) и STOPPED (11:20:23) **нет** записей `AVRCP Play/Pause/Next`. Возможные причины:

1. Кнопки на гарнитуре не нажимались в этот интервал.
2. **Tasker** или другое приложение перехватило MediaSession (см. §11 отчёта).
3. В v1.3.1 не было `onMediaButtonEvent` — часть гарнитур шлёт `HEADSETHOOK` только через `KeyEvent`, не через `onPlay()`. **Исправлено в v1.3.2.**

### Проблема 2: Supabase INSERT failed

- Анонимная сессия создалась (`Anonymous session ok uid=…`).
- Все INSERT в `messages` вернули ошибку (раньше текст ошибки не логировался).
- **Исправлено в v1.3.2:** в лог пишется полный текст исключения PostgREST.

### Отличия от AndroidChat (на момент теста)

| Функция | AndroidChat | AndroidEnglishTutor v1.3.1 |
|---------|-------------|----------------------------|
| `onMediaButtonEvent` + KeyEvent | ✅ | ❌ → ✅ в 1.3.2 |
| Симуляция Play в UI | ✅ | ❌ → ✅ в 1.3.2 |
| Текст ошибки Supabase INSERT | частично | ❌ → ✅ в 1.3.2 |
| Категория лога гарнитуры | `Headset` | `HeadsetTest` → `Headset` в 1.3.2 |

### Исправления v1.3.2–v1.3.3

| Версия | Изменение |
|--------|-----------|
| 1.3.2 | `onMediaButtonEvent`, текст ошибки Supabase INSERT, категория лога `Headset` |
| 1.3.3 | UI как AndroidChat (3 вкладки, большой счётчик), автозапуск сервиса, без audio focus и TTS в тесте |

### Рекомендуемый тест (v1.3.3+)

1. Выключить Tasker-профиль **BT Headset Play Pause**.
2. Окно тестов → вкладка **BT Play** → `Native capture: ON`.
3. «Симулировать Play» → счётчик `1`.
4. Play на гарнитуре → счётчик растёт, журнал обновляется.
5. Логи → `[Headset] BT button Play via native`.
