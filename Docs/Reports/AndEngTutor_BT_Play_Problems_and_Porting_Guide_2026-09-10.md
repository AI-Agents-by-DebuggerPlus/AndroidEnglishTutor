# AndEngTutor BT Play: проблемы, решения и порт функционала

**Дата:** 2026-09-10  
**Сборка:** v1.5.6 (versionCode 27)  
**Пакет:** `com.englishtutor`  
**Эталон поведения:** AndroidChatBtTest95 / BT_Play  

Документ описывает, **какие проблемы** были у теста BT Play, **как их решили**, и **как перенести** тот же функционал в другое Android-приложение (с именами методов и алгоритмами).

См. также краткий how-to: [../headset-testing.md](../headset-testing.md).

---

## 1. Подтверждение по логам (v1.5.6)

Сессия ~20:30 (Pixel Buds Pro 2):

| Жест | В логе | Счётчик |
|------|--------|---------|
| Одиночный Play | `Play pending` → `BT button Play` → `isolation (counter only)` | Play +1 |
| Двойной (Next) | `MEDIA_NEXT → Next (suppress Play 1000ms)` + `Suppressed Play after Next: MEDIA_PLAY` | Next +1, Play без лишнего |

Пример (фрагмент):

```
HARDWARE in: MEDIA_NEXT … Δ=2366ms
HARDWARE: MEDIA_NEXT … → Next (suppress Play 1000ms)
HARDWARE in: MEDIA_PLAY …
Suppressed Play after Next: MEDIA_PLAY …
BT button Next …
```

---

## 2. Какие были проблемы и как решили

### 2.1. Simulate работает, физический Play — нет

**Симптом:** UI «Симулировать Play» увеличивает счётчик (`SIMULATED`), кнопка на гарнитуре молчит.  
**Диагностика (`dumpsys media_session`):** у приложения `MediaSession active=true`, но **`Media button session is null`** (или #1 у YouTube / TTS).  
**Причина:** Android отдаёт media-кнопки сессии с «реальным» media playback под UID приложения. Cue через **Google TTS** атрибутируется `com.google.android.tts`, не `com.englishtutor`.  
**Решение (v1.5.1):** при force-reassert короткий **USAGE_MEDIA** playback через `AudioTrack` в `MediaPlaybackPulse.pulse()`, затем снова claim PLAYING→PAUSED на main thread.

Файлы: `MediaPlaybackPulse.kt`, `HeadsetMonitorService.attachMediaSession(forceReattach)`.

### 2.2. Нужен надёжный захват сессии

**Решение (паттерн BtTest95):**

1. Foreground Service типа `mediaPlayback` — `HeadsetMonitorService`.
2. `requestAudioFocus(USAGE_MEDIA)`.
3. `MediaSessionCompat` + флаги media buttons / transport.
4. Claim: `STATE_PLAYING` → сразу `STATE_PAUSED`.
5. Pulse + `refreshClaimPlaybackState()` после pulse.
6. Reassert при входе в тесты, смене вкладки BT Play, ACL connect/disconnect.

API: `HeadsetMonitorService.start` / `reassert` / `stop`.

### 2.3. Двойное нажатие не давало Next

**Симптом:** пользователь жмёт два раза — растёт только Play (или один раз).  
**Причины:**

1. **Pixel Buds** на двойной жест часто шлёт **`KEYCODE_MEDIA_NEXT`**, а не два `MEDIA_PLAY`. В isolation non-play раньше **игнорировался** (DEBUG).
2. Второй тап иногда приходит как **`MEDIA_PAUSE`**, не Play — тоже не входил в «play label».
3. Окно двойного жеста было жёстко 400 мс и не настраивалось.

**Решение (v1.5.4–1.5.5):**

- Считать `MEDIA_NEXT` → `HeadsetTestController.recordBtNextEvent`.
- Жест Play: `isBtPlayGestureLabel` = Play **и** Pause / Hook.
- Настраиваемый `next_double_tap_ms` в `HeadsetButtonPreferences`.

### 2.4. При Next срабатывал и Play

**Симптом (лог v1.5.5):** в одну миллисекунду `MEDIA_NEXT` + `MEDIA_PLAY` → Next, затем через интервал ещё Play.  
**Причина:** прошивка/AVRCP шлёт **пару** событий; Play уходил в pending и коммитился отдельно.  
**Решение (v1.5.6):** после Next выставлять `suppressPlayUntilMs = now + nextDoubleTapMs`; входящий Play в этом окне → `Suppressed Play after Next` (не в счётчик). Отмена pending job + `pendingGeneration`.

### 2.5. Защита от повторного нажатия vs интервал Next

Это **две разные** настройки:

| | Защита (debounce) | Интервал Next |
|--|-------------------|---------------|
| Смысл | После уже засчитанного события игнор дребезга | Ждать 2-й жест / suppress companion Play |
| Вкл/выкл | Switch | Всегда активен |
| Pref | `debounce_enabled`, `debounce_interval_ms` | `next_double_tap_ms` |

---

## 3. Поток данных (для порта)

```
KeyEvent / AVRCP
  → MediaSessionCompat.Callback
       onMediaButtonEvent / onPlay / onSkipToNext / …
  → Notifier.notifyButton(label, source)
  → (логика debounce / Next / suppress)
  → TestController.recordBtPlayEvent / recordBtNextEvent
  → UI StateFlow
```

В AndEngTutor при открытии тестов:

- `VoiceTestViewModel.enterHeadsetIsolation()` → `btPlayTestIsolation = true`
- `HeadsetMonitorService.reassert()` + TTS cue «BT test ready»
- Вне тестов isolation OFF → `EnglishTutorPlayHandler` (урок)

---

## 4. Компоненты и методы (карта для переноса)

Скопируйте логику пакетов `session/` + минимум UI; DI (Hilt) можно заменить на свои синглтоны.

### 4.1. Имена кнопок — `HeadsetButtonNames`

| Метод | Назначение |
|-------|------------|
| `fromKeyCode(code: Int): String?` | `KeyEvent` → `MEDIA_PLAY` / `PAUSE` / `NEXT` / `HEADSETHOOK` / … |
| `normalize(label: String): String` | Uppercase trim |
| `isBtPlayLabel(label)` | Только Play / PlayPause / Hook |
| `isBtPlayGestureLabel(label)` | + **Pause** (для двойного жеста) |
| `displayLabel(label)` | Человекочитаемая метка в журнале |

### 4.2. Pulse — `MediaPlaybackPulse.pulse()`

Суть для порта:

```kotlin
// USAGE_MEDIA + CONTENT_TYPE_MUSIC
// AudioTrack MODE_STATIC, ~180 ms, тихий 440 Hz
// play → sleep(duration+40) → stop/release
fun pulse()
```

Без этого `dumpsys` часто показывает `Media button session is null` при одном только TTS.

### 4.3. Сервис — `HeadsetMonitorService`

| Метод | Назначение |
|-------|------------|
| `onCreate()` | Notification channel, FGS, `attachMediaSession(force=true)`, ACL listener |
| `onStartCommand` | `EXTRA_FORCE_REASSERT` → force attach |
| `attachMediaSession(forceReattach)` | Release old; AudioFocus; new `MediaSessionCompat`; callbacks; PLAYING→PAUSED; optional pulse |
| `refreshClaimPlaybackState()` | Повторный claim после pulse (main) |
| `requestAudioFocus()` | `AudioFocusRequest` USAGE_MEDIA |
| Companion `start` / `reassert` / `stop` | Запуск FGS / force / stop |

Callback (идея):

```kotlin
override fun onMediaButtonEvent(intent: Intent?): Boolean {
    val event = /* KEY_EVENT from intent */
    val label = HeadsetButtonNames.fromKeyCode(event.keyCode)
    if (label != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        notifier.notifyButton(label, source = "hardware-mediaButtonEvent")
        return true
    }
    return super.onMediaButtonEvent(intent)
}
override fun onPlay() = notifier.notifyButton("MEDIA_PLAY", "hardware-callback-onPlay")
override fun onSkipToNext() = notifier.notifyButton("MEDIA_NEXT", "hardware-callback-onNext")
// onPause / onSkipToPrevious / onStop аналогично
```

Manifest (обязательно):

- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- Service `foregroundServiceType="mediaPlayback"`
- (опц.) `NotificationListenerService` для списка ActiveSessions

### 4.4. Диспетчер — `HeadsetButtonNotifier`

| Метод / поле | Назначение |
|--------------|------------|
| `notifyButton(buttonLabel, source)` | Единая точка входа |
| `eventKind(source)` | `"HARDWARE"` если source содержит hardware/mediabuttonevent/callback-on, иначе `"SIMULATED"` |
| `handlePlayGesture(...)` | Debounce / double → Next / pending Play |
| `handleHardwareNext(...)` | Next + `suppressPlayUntilMs` + cancel pending |
| `dispatchPlay(...)` | `recordBtPlayEvent` + isolation или lesson |
| `btPlayTestIsolation` | Тест: только счётчики |
| `isolatedBtPlayHandler` | Опц. хук (у нас STT tab) |

Алгоритм жеста Play (псевдокод):

```text
on PlayOrPause:
  if now < suppressPlayUntilMs → ignore ("Suppressed Play after Next")
  else if pending && (now - lastGesture) <= nextDoubleTapMs
    → cancel pending; commit Next; suppressPlayUntil = now + nextDoubleTapMs
  else if debounceEnabled && within debounceInterval after last commit
    → ignore ("Debounced")
  else
    → lastGesture = now; schedule after nextDoubleTapMs → commit Play

on MEDIA_NEXT:
  cancel pending
  suppressPlayUntil = now + nextDoubleTapMs
  commit Next
```

### 4.5. Настройки — `HeadsetButtonPreferences`

SharedPreferences `headset_button_prefs`:

| Ключ | Default | Методы |
|------|---------|--------|
| `debounce_enabled` | true | `setDebounceEnabled` |
| `debounce_interval_ms` | 500 | `setDebounceIntervalMs` (50…5000) |
| `next_double_tap_ms` | 400 | `setNextDoubleTapMs` (50…5000) |

StateFlow `state: HeadsetButtonPrefs` для UI.

### 4.6. Счётчики — `HeadsetTestController`

| Метод | Назначение |
|-------|------------|
| `setCaptureStatus(nativeCaptureOn)` | ON/OFF в UI |
| `recordBtPlayEvent(label, source, kind)` | `pressCount++`, строка журнала |
| `recordBtNextEvent(source, kind, viaDoublePlay)` | `nextCount++` (`Next` или `Next (2×Play)`) |
| `resetCounter()` | Обнулить Play/Next/log |
| `state: StateFlow<HeadsetTestState>` | UI |

### 4.7. UI теста — `VoiceTestViewModel` / `VoiceTestScreen`

| Метод VM | Назначение |
|----------|------------|
| `enterHeadsetIsolation` / `exitHeadsetIsolation` | Флаг isolation + start monitor |
| `reassertBtPlayCapture(speakCue)` | `HeadsetMonitorService.reassert` + cue |
| `simulateBtPlay()` | `notifyButton("MEDIA_PLAY", "ui-simulate")` |
| `setDebounceEnabled` / `onDebounceIntervalTextChanged` / `commitDebounceInterval` | Prefs |
| `onNextDoubleTapTextChanged` / `commitNextDoubleTapInterval` | Prefs |
| `resetBtPlayCounter()` | Reset |

Compose: `BtPlayTestSection` — switch, два поля мс, счётчики Play|Next, Reassert, Simulate, журнал.

### 4.8. Диагностика (желательно при порте)

| Класс | Роль |
|-------|------|
| `HeadsetDiagnosticsHelper.collect(...)` | Чеклист готовности |
| `ActiveSessionsHelper.snapshot` | Кто #1 (нужен Notification Access) |
| `TaskerConflictChecker` | WARN про Grab media keys |
| `NoOpNotificationListener` | Включает чтение ActiveSessions |

---

## 5. Минимальный чеклист порта в другое приложение

1. FGS `mediaPlayback` + `MediaSessionCompat` + AudioFocus USAGE_MEDIA.  
2. Claim PLAYING→PAUSED; **обязательно** USAGE_MEDIA pulse под своим UID при reassert.  
3. `onMediaButtonEvent` (DOWN, repeatCount==0) + transport callbacks → один `notifyButton`.  
4. Логика Next / suppress companion Play (Buds шлёт NEXT+PLAY).  
5. Жест: Play **и** Pause в одном окне `nextDoubleTapMs`.  
6. Опциональный debounce.  
7. Тестовый UI: isolation (не слать в бизнес-логику), счётчики, Simulate, Reassert, dumpsys-проверка.  
8. Перед тестом: force-stop YouTube; выключить Tasker Grab.

Проверка:

```bash
adb shell dumpsys media_session | findstr /i "Media button session"
# ожидается ваш package / session tag
```

---

## 6. Версии по вехам

| Версия | Что дали |
|--------|----------|
| 1.5.1 | USAGE_MEDIA pulse → HARDWARE Play |
| 1.5.2–1.5.3 | Debounce UI; кнопка выключения на Home |
| 1.5.4 | Настраиваемый интервал Next |
| 1.5.5 | Pause+NEXT в жесте; INFO `Δ=ms` |
| **1.5.6** | Suppress Play после Next — Next без ложного Play |

---

## 7. Исходники в репозитории

```
app/src/main/java/com/englishtutor/session/
  HeadsetMonitorService.kt
  MediaPlaybackPulse.kt
  HeadsetButtonNotifier.kt
  HeadsetButtonPreferences.kt
  HeadsetTestController.kt
  HeadsetButtonNames.kt
  EnglishTutorPlayHandler.kt
app/src/main/java/com/englishtutor/ui/screens/voicetest/
  VoiceTestScreen.kt
  VoiceTestViewModel.kt
app/src/main/java/com/englishtutor/bluetooth/
  HeadsetDiagnosticsHelper.kt
  ActiveSessionsHelper.kt
  TaskerConflictChecker.kt
  NoOpNotificationListener.kt
```
