# Промпт для Cursor: нет реакции на кнопки Bluetooth-гарнитуры (вкладка BT Play)

## Контекст

Репозиторий: `AndroidEnglishTutor` (пакет `com.englishtutor`), текущая версия 1.3.3 (versionCode 9).

Симптом: на экране тестов → вкладка **BT Play** нажатия физических кнопок гарнитуры (Pixel Buds Pro 2) не дают никакой реакции — счётчик не растёт, событий в логе (`Логи → Headset`) нет.

Уже подтверждено диагностикой:
- Кнопка **«Симулировать Play»** на той же вкладке работает — счётчик растёт. Значит цепочка `UI → ViewModel → HeadsetTestController.notifyButton()` исправна.
- Проблема — именно в доставке AVRCP-события от гарнитуры до `MediaSessionCompat.Callback` в `HeadsetTestService`.

## Основная гипотеза (проверить и исправить в первую очередь)

В `Docs/cursor_prompt_language_tutor_mvp.md` (исходное ТЗ проекта, раздел «Взаимодействие только через кнопки Bluetooth-гарнитуры», п. 1) прямо указано:

> Захватывать audio focus (`AudioManager.requestAudioFocus`) на время сессии урока, чтобы гарнитура слала события именно приложению

Сравнение двух сервисов в `app/src/main/java/com/englishtutor/session/`:

- **`LessonSessionService.kt`** — вызывает `ensureAudioFocus()` (запрашивает `AudioManager.requestAudioFocus`) при старте урока и при каждом media-событии.
- **`HeadsetTestService.kt`** — **не запрашивает audio focus вообще**. Он только создаёт `MediaSessionCompat`, вызывает `isActive = true` и держит foreground-уведомление.

Без захвата audio focus система не считает это приложение приоритетным получателем физических кнопок гарнитуры (особенно при наличии других активных MediaSession/аудио-приложений на устройстве, включая Tasker). Отсюда вероятная причина: `HeadsetTestService` создаёт валидную, но «пассивную» MediaSession, которая не получает реальные AVRCP-нажатия, хотя программные вызовы `notifyButton()` (симуляция) работают, так как идут в обход системы.

## Задача 1 — исправить `HeadsetTestService`

Добавь захват audio focus по аналогии с `LessonSessionService.ensureAudioFocus()` / `abandonAudioFocus()`:

1. В `HeadsetTestService` добавь поля `audioManager: AudioManager?`, `audioFocusRequest: AudioFocusRequest?`, `hasAudioFocus: Boolean`.
2. В `onCreate()` инициализируй `audioManager = getSystemService(AUDIO_SERVICE) as AudioManager`.
3. Добавь `AudioManager.OnAudioFocusChangeListener`, аналогичный тому, что в `LessonSessionService` (минимально — можно просто логировать смену фокуса через `AppLogger`, не завязывая на бизнес-логику теста).
4. Добавь метод `ensureAudioFocus()`, который запрашивает `AUDIOFOCUS_GAIN` с `AudioAttributes.USAGE_MEDIA` / `CONTENT_TYPE_SPEECH` (Android O+) или legacy `requestAudioFocus` (ниже O), и вызывай его:
   - сразу после `attachMediaSession()` в `onCreate()`;
   - повторно в `onStartCommand()`, если `hasAudioFocus == false`.
5. Добавь `abandonAudioFocus()` и вызови его в `stopTest()` и `onDestroy()`, симметрично `LessonSessionService`.
6. **Не переусложняй**: тестовому сервису не нужна логика паузы/дакинга при потере фокуса — только сам факт удержания фокуса, чтобы AVRCP-кнопки маршрутизировались сюда.

## Задача 2 — диагностическое логирование (оставить даже после фикса)

В `HeadsetTestService.onCreate()` перед `attachMediaSession()` добавь однократный лог через `AppLogger` (тег `"Headset"`) с состоянием: держит ли сервис audio focus, активна ли MediaSession, и после успешного `requestAudioFocus` — залогируй результат (`AUDIOFOCUS_REQUEST_GRANTED` / `FAILED` / `DELAYED`). Это нужно, чтобы при следующем тесте в экране «Логи» сразу было видно, была ли получена audio focus, без пересборки APK с новыми breakpoint'ами.

## Задача 3 — проверка через adb (сделай сам, укажи результат в PR/комментарии)

Перед тем как считать фикс подтверждённым, выполни на подключённом устройстве:

```
adb shell dumpsys media_session
```

и убедись, что:
- `com.englishtutor` присутствует в списке активных сессий **на вершине** (или единственный активный получатель) в момент теста BT Play;
- ни один пакет Tasker (`net.dinglisch.android.taskerm` или аналог) не выше `com.englishtutor` в этом списке в момент нажатия.

Если Tasker всё ещё выше — это отдельная, уже задокументированная проблема (см. `Docs/AndroidChat-BT-Headset-Testing-Report.md`, раздел 11, п. 1: конфликт между Tasker Media Button profile и нативным capture). В этом случае в PR явно укажи, что audio focus сам по себе не решает конфликт с Tasker, и нужно временно отключать Tasker-профиль **BT Headset Play Pause** при тестировании.

## Критерии готовности

- [ ] `HeadsetTestService` запрашивает и удерживает audio focus всё время, пока активна вкладка BT Play, и корректно освобождает его в `stopTest()`/`onDestroy()`.
- [ ] Реальное нажатие Play на Pixel Buds Pro 2 при выключенном Tasker-профиле приводит к росту счётчика на вкладке BT Play и записи в `Логи → Headset`.
- [ ] В логах виден результат `requestAudioFocus` (granted/failed) для последующей диагностики без пересборки.
- [ ] Не затронута логика `LessonSessionService` и `HeadsetPlayHandler` — правки только в `HeadsetTestService.kt`.
