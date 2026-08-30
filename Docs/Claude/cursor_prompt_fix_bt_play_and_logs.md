# Промпт для Cursor: доводка BT Play (HeadsetMonitorService) + чистка логов

## Контекст

Репозиторий `AndroidEnglishTutor`, коммит `461cd7a` («Add Bluetooth headset support and AndroidChat-aligned media button handling»). Архитектура кнопок гарнитуры уже переписана по образцу AndroidChat (`HeadsetMonitorService` + `HeadsetButtonNotifier` + `EnglishTutorPlayHandler`), Tasker больше не используется, `LessonSessionService` больше не создаёт вторую `MediaSession` — это правильно и трогать не нужно.

Найдены при статическом анализе кода (без физического устройства) две отдельные проблемы:

1. Вкладка **Tests → BT Play** всё ещё не всегда реагирует на реальную гарнитуру.
2. Экран **Логи** засоряется избыточными и малополезными записями.

Ниже — конкретные точечные правки. Не переписывай архитектуру целиком, только перечисленные места.

---

## Задача 1 — `PlaybackStateCompat`: PAUSED → PLAYING

Файл: `app/src/main/java/com/englishtutor/session/HeadsetMonitorService.kt`, метод `attachMediaSession()`.

Сейчас:
```kotlin
setPlaybackState(
    PlaybackStateCompat.Builder()
        .setActions(...)
        .setState(PlaybackStateCompat.STATE_PAUSED, 0, 0f)
        .build(),
)
```

Замени `PlaybackStateCompat.STATE_PAUSED` на `PlaybackStateCompat.STATE_PLAYING`. На части устройств/прошивок Android приоритизирует при маршрутизации AVRCP-кнопки именно «играющую» `MediaSession`, а не просто активную. Это чистый foreground-мониторинг кнопок (реального аудио не проигрывает), поэтому STATE_PLAYING здесь корректен семантически — сессия «слушает» кнопки, а не музыку.

Проверь, что это не ломает поведение `LessonSessionService` — он свою `MediaSession` не создаёт, так что конфликта состояний быть не должно.

---

## Задача 2 — статус «Native capture: ON» должен отражать реальность

Файл: `app/src/main/java/com/englishtutor/EnglishTutorApp.kt`.

Сейчас:
```kotlin
override fun onCreate() {
    super.onCreate()
    logger.i("App", "Started · ${AppVersion.label}")
    bluetoothConnectionMonitor.ensureStarted(this)
    HeadsetMonitorService.start(this)
    headsetTestController.setCaptureStatus(nativeCaptureOn = true)
}
```

Проблема: `setCaptureStatus(true)` вызывается безусловно, сразу после `start()`, без подтверждения что сервис реально поднялся и `MediaSessionCompat.isActive == true`. Если запуск не удастся, UI всё равно покажет «ON».

Сделай подтверждение статуса из самого сервиса, а не из `Application`:

1. В `HeadsetMonitorService` добавь вызов `headsetTestController.setCaptureStatus(nativeCaptureOn = true)` **внутри** `attachMediaSession()`, сразу после `isActive = true` — то есть статус подтверждается там, где сессия реально стала активной, а не в предположении, что `start()` сработал.
2. В `onDestroy()` сервиса вызови `headsetTestController.setCaptureStatus(nativeCaptureOn = false)` перед освобождением сессии — чтобы при остановке (`AppSessionManager.stopApp()`) статус в UI не оставался ложным «ON».
3. В `EnglishTutorApp.onCreate()` убери прямой вызов `headsetTestController.setCaptureStatus(nativeCaptureOn = true)` — источник истины теперь только сервис.
4. Оберни `HeadsetMonitorService.start(this)` в `runCatching { ... }.onFailure { logger.e("App", "HeadsetMonitorService failed to start: ${it.message}") }`, чтобы сбой запуска на холодном старте не проходил незаметно и был виден в логах явной ERROR-записью.

---

## Задача 3 — убрать дублирующий 2-секундный poll Bluetooth-состояния

Файл: `app/src/main/java/com/englishtutor/ui/screens/voicetest/VoiceTestViewModel.kt`, блок `init`:

```kotlin
viewModelScope.launch {
    while (isActive) {
        delay(BLUETOOTH_REFRESH_MS)
        bluetoothConnectionMonitor.refresh(appContext)
    }
}
```

Это дублирует `BluetoothConnectionMonitor`'s собственный ACL `BroadcastReceiver`, который уже вызывает `refresh()` и логирует при каждом реальном событии подключения/отключения (`onEvent` в `BluetoothConnectionMonitor.ensureStarted()`).

Замени постоянный poll на однократный `refresh()` при открытии экрана плюс редкий «страховочный» poll большего интервала (на случай пропущенного ACL-события), без логирования на каждый тик:

1. Оставь единственный `bluetoothConnectionMonitor.refresh(appContext)` сразу в `init` (уже есть).
2. Увеличь интервал страховочного poll минимум до 15–20 секунд (`BLUETOOTH_REFRESH_MS = 15_000L` вместо `2_000L`) — это достаточно для UI-обновления карточки устройств, но не даёт полю `logFingerprint()` реагировать на каждое мимолётное переключение `isBluetoothScoOn`/`isBluetoothA2dpOn`.
3. В `BluetoothDeviceHelper.logFingerprint()` (`BluetoothDeviceHelper.kt`) исключи `activeDevice` из отпечатка, используемого именно для решения «логировать или нет» в `BluetoothConnectionMonitor.refresh()` — оставь его только в самом `_snapshot.value` для UI. То есть: список **подключённых** устройств меняется редко и логировать его смену — полезно; активный **route** (SCO/A2DP/COMMUNICATION) может переключаться самим Android многократно без внешнего события — логировать каждое такое переключение не нужно. Сделай для лога отдельный, более грубый отпечаток, например только по списку адресов подключённых устройств: `devices.joinToString { it.address }`.

---

## Задача 4 — уровень логирования в `AppLogger`

Файл: `app/src/main/java/com/englishtutor/util/AppLogger.kt`.

Сейчас все уровни (DEBUG/INFO/WARN/ERROR) равноправно попадают в один кольцевой буфер на 500 записей, который показывается в экране «Логи» и целиком уходит при ручной отправке в Supabase.

1. Добавь в `AppLogger` настраиваемый минимальный уровень для **хранения в буфере** (не для logcat — в `android.util.Log.println` пусть уходит всё как сейчас):
   ```kotlin
   @Volatile
   var minBufferLevel: LogLevel = LogLevel.INFO
   ```
2. В `log(...)` добавляй запись в `_entries` только если `level.ordinal >= minBufferLevel.ordinal`, оставляя `android.util.Log.println(...)` без изменений (то есть в logcat через adb DEBUG видно всегда, а в буфере/Supabase — по умолчанию только INFO и выше).
3. На экране «Логи» (`LogsViewModel`/соответствующий UI) добавь простой тумблер «Показывать DEBUG», переключающий `appLogger.minBufferLevel` между `DEBUG` и `INFO` — чтобы при реальной отладке BT Play можно было включить подробности одной кнопкой, а по умолчанию буфер оставался читаемым.
4. Замени в `HeadsetButtonNotifier.notifyButton()` и `EnglishTutorPlayHandler.onMediaButton()` уровень для строки «Debounced: …» и «Ignored headset button …» — они уже `logger.d(...)`, менять код не нужно, это просто будет отфильтровано новым уровнем по умолчанию.

---

## Задача 5 — диагностическая заметка в чеклист (не код, но важно)

В `Docs/AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md`, раздел §10.4 «Чеклист приёмки BT», уже есть пункт про выключение Tasker BT Key. Добавь туда ещё один пункт, привязанный к задаче 1:

- [ ] `adb shell dumpsys media_session` в момент нажатия Play показывает `com.englishtutor` как единственную/верхнюю активную сессию, а не только `HeadsetMonitorService` в списке процессов

Это не код-фикс, а инструкция для ручной проверки после сборки — оставь как чеклист-пункт, не как логику в коде.

---

## Критерии готовности

- [ ] `HeadsetMonitorService` выставляет `STATE_PLAYING`, статус capture подтверждается из самого сервиса (не из `Application.onCreate()` заранее).
- [ ] Сбой запуска `HeadsetMonitorService` виден как ERROR-запись в логах, а не проглатывается молча.
- [ ] На экране Tests больше нет постоянного 2-секундного лог-потока по Bluetooth-состоянию в покое; список подключённых устройств по-прежнему логируется при реальном ACL-событии.
- [ ] `AppLogger` по умолчанию не показывает DEBUG-записи в буфере/Supabase-выгрузке; на экране «Логи» есть тумблер для включения DEBUG вручную.
- [ ] Реальное нажатие Play на Pixel Buds Pro 2 (при выключенном Tasker BT Key) увеличивает счётчик на вкладке BT Play и оставляет INFO-запись `BT button … via native` в логе.
