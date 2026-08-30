# Промпт для Cursor: HeadsetMonitorService не самовосстанавливается (по сравнению с AndroidChat)

## Контекст

Репозиторий `AndroidEnglishTutor`, актуальный `master` (после коммита `461cd7a`, где кнопки гарнитуры переписаны по образцу AndroidChat).

Проведено прямое построчное сравнение с **рабочим** референсом — `AndroidChat` из репозитория `TaskerToWpf` (`AndroidChat/app/src/main/java/com/taskertowpf/androidchat/headset/HeadsetMonitorService.kt`). Сам класс `HeadsetMonitorService` в обоих проектах практически идентичен (включая `PlaybackStateCompat.STATE_PAUSED` — это подтверждённо рабочее значение в AndroidChat, менять его на `STATE_PLAYING` **не нужно**; это отменяет рекомендацию из предыдущего промпта `cursor_prompt_fix_bt_play_and_logs.md`, задача 1 — её выполнять не надо).

Разница — не в самом сервисе, а в том, **кто и когда его (пере)запускает**.

---

## Подтверждённая причина

**AndroidChat** запускает `HeadsetMonitorService.start()` многократно за сессию — при каждом входе на экран тестов, а не один раз при старте процесса:

```kotlin
// AndroidChat/.../MainViewModel.kt
fun openTests() {
    closeLessonInternal()
    enterHeadsetIsolation()
    ...
}

private fun enterHeadsetIsolation() {
    app.speechService.stopSpeaking()
    app.voiceInputService.cancel()
    app.headsetButtonNotifier.btPlayTestIsolation = true
    if (_uiState.value.settings.enableNativeHeadsetCapture) {
        HeadsetMonitorService.start(getApplication())   // ← перезапуск при каждом входе
    }
}
```

Плюс ещё раз — в `updateHeadsetMonitor()` при подключении.

**AndroidEnglishTutor** запускает сервис **один раз** за весь жизненный цикл процесса — в `EnglishTutorApp.onCreate()` — и больше нигде:

```kotlin
// EnglishTutorApp.kt
override fun onCreate() {
    super.onCreate()
    ...
    HeadsetMonitorService.start(this)   // единственный вызов start() во всём проекте
    headsetTestController.setCaptureStatus(nativeCaptureOn = true)
}
```

`VoiceTestViewModel.enterHeadsetIsolation()` при входе на экран Tests сервис **не перезапускает** — только выставляет флаг изоляции:

```kotlin
// VoiceTestViewModel.kt
private fun enterHeadsetIsolation() {
    textToSpeech.stopSpeaking()
    speechRecognizer.cancel()
    stopLessonSession()
    headsetButtonNotifier.btPlayTestIsolation = true
    refreshIsolatedHandler()
    // нет HeadsetMonitorService.start()
}
```

Из-за этого `HeadsetMonitorService` в AndroidEnglishTutor — не самовосстанавливающийся. Если он хоть раз перестанет жить (явный `AppSessionManager.stopApp()` → `HeadsetMonitorService.stop()`, агрессивное управление памятью прошивки, убитый в фоне foreground-процесс), реальные AVRCP-кнопки гарнитуры принимать физически некому — а счётчик через «Симулировать Play» продолжает расти, потому что `HeadsetButtonNotifier` — Hilt-синглтон, вызывается из UI напрямую, в обход сервиса. Это и есть расхождение между «симуляция работает» и «реальная кнопка — нет».

---

## Задача — сделать `HeadsetMonitorService` самовосстанавливающимся

Файл: `app/src/main/java/com/englishtutor/ui/screens/voicetest/VoiceTestViewModel.kt`.

1. Добавь в конструктор зависимость на context (уже есть `@ApplicationContext private val appContext: Context`) — ничего добавлять не нужно, использовать существующий.

2. В `enterHeadsetIsolation()` добавь явный перезапуск сервиса **перед** выставлением флага изоляции, по образцу AndroidChat:

```kotlin
private fun enterHeadsetIsolation() {
    textToSpeech.stopSpeaking()
    speechRecognizer.cancel()
    stopLessonSession()
    com.englishtutor.session.HeadsetMonitorService.start(appContext)
    headsetButtonNotifier.btPlayTestIsolation = true
    refreshIsolatedHandler()
    logger.i(TAG, "Headset isolation ON")
}
```

   (Импортируй `HeadsetMonitorService` обычным `import` в начале файла вместо полного пути — здесь путь указан явно только чтобы не перепутать класс.)

   `HeadsetMonitorService.start()` уже идемпотентен на уровне `ContextCompat.startForegroundService()` — повторный вызов на уже работающий сервис безопасен (`onStartCommand` проверяет `mediaSession?.isActive != true` и переустанавливает сессию только при необходимости), поэтому лишнего дублирования MediaSession не будет.

3. Добавь такой же перезапуск в `AppSessionManager`, симметрично тому, как AndroidChat переустанавливает сервис при повторном подключении (`updateHeadsetMonitor()`), — а именно: после `stopApp()` больше нельзя тихо остаться без сервиса до перезапуска процесса. Добавь публичный метод:

```kotlin
// AppSessionManager.kt
fun restartHeadsetMonitor() {
    runCatching { HeadsetMonitorService.start(appContext) }
        .onFailure { logger.e("App", "HeadsetMonitorService restart failed: ${it.message}") }
}
```

   и вызови его там же, где сейчас исправляется статус capture из задачи 2 предыдущего промпта (`HeadsetMonitorService.attachMediaSession()` подтверждает `nativeCaptureOn = true`) — то есть при любом переходе в состояние «сервис должен работать» (открытие Tests, старт урока) вызывай либо напрямую `HeadsetMonitorService.start()`, либо через этот метод, а не полагайся на то, что он один раз стартовал в `Application.onCreate()`.

4. Не убирай вызов в `EnglishTutorApp.onCreate()` — он остаётся как первичный старт при холодном запуске; добавленный вызов в `enterHeadsetIsolation()` — это именно недостающий повторный запуск при каждом входе на экран тестов, аналогично AndroidChat.

---

## Критерии готовности

- [ ] `HeadsetMonitorService.start()` вызывается не только в `EnglishTutorApp.onCreate()`, но и при каждом входе на экран Tests (`VoiceTestViewModel.enterHeadsetIsolation()`), как в AndroidChat.
- [ ] Если сервис был остановлен (`AppSessionManager.stopApp()` или убит системой), повторное открытие экрана Tests восстанавливает `MediaSessionCompat` без перезапуска приложения.
- [ ] `PlaybackStateCompat` в `HeadsetMonitorService` остаётся `STATE_PAUSED` — не менять (подтверждено сравнением с рабочим AndroidChat).
- [ ] Реальное нажатие Play на Pixel Buds Pro 2 после захода на вкладку BT Play увеличивает счётчик и без предварительного пересоздания процесса приложения — в том числе после ранее нажатого «Stop».
