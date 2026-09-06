# Инструкция агенту Cursor: устранить отсутствие HARDWARE BT Play в AndEngTutor

Скопируй этот файл в чат Agent в репозитории **AndroidEnglishTutor** (`com.englishtutor` / AndEngTutor).

**Источник проблемы:**  
[Docs/Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md](../../Reports/AndEngTutor_BT_Play_Implementation_Report_2026-09-06.md)

**Эталон рабочей реализации:**  
`D:\Programming\Cursor\2026\September\BT_Play\AndroidChatBtTest95`  
Инструкция внедрения:  
`D:\Programming\Cursor\2026\September\BT_Play\Docs\Tasks\Cursor\AndroidEnglishTutor_BT_Play_Test_Agent_Instruction.md`

---

## 0. Симптом и уже доказанное

| Наблюдение | Значение |
|---|---|
| Simulate Play | ✅ счётчик + SIMULATED в логе |
| Физический Play (Pixel Buds Pro 2) | ❌ нет HARDWARE, счётчик не растёт |
| MediaSession / AudioFocus / reassert / isolation | ✅ уже есть в коде (см. отчёт §2) |
| Гарнитура подключена (ACL) | ✅ |

**Вывод отчёта:** событие **не доходит** до `MediaSession.Callback`.  
Цепочка внутри приложения после callback исправна.  
Это **не** баг UI-счётчика и **не** «забыли AudioFocus» (старый промпт `Docs/Claude/cursor_prompt_fix_bt_play_no_reaction.md` устарел относительно v1.5.0).

---

## 1. Цель этой инструкции

1. **Доказать**, кто сейчас media-button session в момент нажатия Play.  
2. Если это конкурент (YouTube / Tasker / другое) — убрать конкурента и подтвердить HARDWARE.  
3. Если после чистого окружения HARDWARE всё ещё нет, а **AndroidChatBtTest95 на том же устройстве ловит Play** — догнать Tutor до поведения 95 (code gap).  
4. Если 95 тоже не ловит — проблема окружения/устройства, не Tutor.  
5. Добавить в Tutor диагностику «кто #1», чтобы следующий раз не гадать.

Критерий успеха:

```text
BT button Play via hardware-callback-onPlay (HARDWARE)
HARDWARE: MEDIA_PLAY via hardware-... → isolation (counter only)
```

и рост счётчика на вкладке BT Play.

---

## 2. Фаза A — диагностика (обязательно до правок кода)

Выполни сам через adb. Зафиксируй вывод в короткий отчёт рядом с этим файлом  
(например `Docs/Reports/AndEngTutor_BT_Play_Hardware_Fix_Notes_YYYY-MM-DD.md`).

### A1. Очистить конкурентов

```bash
adb shell am force-stop com.google.android.youtube
adb shell am force-stop com.google.android.apps.youtube.music
adb shell am force-stop com.taskertowpf.androidchatbttest95
adb shell am force-stop com.taskertowpf.androidchatbttest93
adb shell am force-stop com.taskertowpf.androidchatcopyv1
# при наличии других media-приложений — тоже force-stop
```

Вручную / через UI:

- отключить Tasker-профиль **BT Key / Grab / Media Button**, если включён;
- закрыть Google Podcasts / Movies / любой плеер с активной сессией.

### A2. Запустить Tutor и взять дамп ДО Play

```bash
adb shell am start -n com.englishtutor/.MainActivity
# В приложении: Окно тестов → BT Play → Reassert MediaSession → дождаться «BT test ready»

adb shell dumpsys media_session
```

Проверить строки:

- `Media button session is ...`
- `Last MediaButtonReceiver`
- есть ли `com.englishtutor` / `AndEngTutorHeadset` с `active=true`

**Ожидание после Reassert+cue:**  
`Media button session` указывает на `com.englishtutor`.

Если указывает на YouTube / Tasker / другой пакет — это и есть причина; сначала устрани конкурента, код не «чинит» чужую сессию сам по себе без выигрыша приоритета.

### A3. Нажать физический Play и сразу снова dumpsys + logcat

```bash
adb logcat -c
# пользователь жмёт Play на Buds
adb shell dumpsys media_session
adb logcat -d | findstr /i "Headset HARDWARE SIMULATED MEDIA_PLAY MediaSession AudioFocus englishtutor"
```

### A4. A/B с эталоном BtTest95 на том же телефоне

```bash
adb shell am force-stop com.englishtutor
adb shell am start -n com.taskertowpf.androidchatbttest95/.MainActivity
# Tests → BT Play → Reassert → Play на гарнитуре
adb shell dumpsys media_session
```

Интерпретация:

| Tutor HARDWARE | 95 HARDWARE | Вывод |
|---|---|---|
| нет | да | **code gap в Tutor** → Фаза C |
| нет | нет | окружение / Buds / Tasker / система → Фаза B углубить |
| да | * | проблема решена окружением; Фаза D (диагностика) всё равно полезна |

---

## 3. Фаза B — устранение внешних причин (без кода или с минимальным UX)

Если dumpsys показал чужую media-button session:

1. Force-stop конкурента (§A1).
2. AndEngTutor → BT Play → **Reassert**.
3. Повторить Play.

Если в диагностике Tutor `Tasker SET_MEDIA_KEY_LISTENER = выдано`:

1. В Tasker отключить профили перехвата headset/media keys.
2. Либо временно отозвать у Tasker разрешение media-key listener (если доступно в системе).
3. Повторить A2–A3.

Если `POST_NOTIFICATIONS` не выдано — запросить и выдать: FGS-уведомление MediaSession на новых Android может быть ограничено.

Если active audio device = `Pixel 6a · COMMUNICATION`, а не buds:

1. Переподключить Buds / выбрать Buds как media output.
2. Не считать это главной причиной AVRCP, но устранить перед повторным тестом.

**Не закрывай задачу только советом «закройте YouTube»**, если A/B показал: 95 ловит, Tutor — нет. Тогда обязательна Фаза C.

---

## 4. Фаза C — code gap (если 95 ловит, Tutor нет)

Сверяй Tutor с эталоном построчно по поведению, не «на глаз».

### C1. Эталонные файлы BT_Play

| Тема | Эталон |
|---|---|
| MediaSession + AudioFocus + PLAYING→PAUSED + reassert | `BT_Play/AndroidChatBtTest95/.../headset/HeadsetMonitorService.kt` |
| HARDWARE/SIMULATED + isolation | `.../headset/HeadsetButtonNotifier.kt` |
| Reassert + cue при открытии BT tab | `.../MainViewModel.kt` (`reassertBtPlayCapture`, `openTests`) |
| UI | `.../ui/TestsScreen.kt` |

### C2. Файлы Tutor, которые править/проверять

| Файл | Что проверить |
|---|---|
| `app/.../session/HeadsetMonitorService.kt` | Полный паритет с 95: AudioFocus **до** session, PLAYING→PAUSED, force reattach, main-thread callback, ACL reattach |
| `app/.../session/HeadsetButtonNotifier.kt` | hardware sources дают HARDWARE; isolation не глотает событие до UI |
| `app/.../ui/screens/voicetest/VoiceTestViewModel.kt` | На tab BT Play: isolation ON, stop lesson session, `reassert` + cue |
| `app/.../session/LessonSessionService.kt` | Не перехватывает AudioFocus обратно во время BT-теста |
| `app/.../EnglishTutorApp.kt` / `AppSessionManager.kt` | Не stop’ает `HeadsetMonitorService` во время теста |
| `AndroidManifest.xml` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, сервис `foregroundServiceType="mediaPlayback"` |

### C3. Конкретные усиления, которых может не хватать относительно «просто isActive»

Сделать по приоритету (после подтверждения A/B):

1. **Silent / minimal USAGE_MEDIA pulse при reassert**  
   Как идея из BtTest93 `REAL_MEDIA_PLAYBACK` / CopyV1 MediaPlayer: короткое воспроизведение через `MediaPlayer` + `AudioAttributes.USAGE_MEDIA` (даже тихий/короткий файл или TTS→file), чтобы система назначила Tutor media-button session.  
   Сейчас cue идёт через обычный TTS — на части прошивок этого недостаточно.

2. **Явный `setMediaButtonReceiver` / session activity**  
   Сверить с 95: флаги `FLAG_HANDLES_MEDIA_BUTTONS | FLAG_HANDLES_TRANSPORT_CONTROLS`, actions включают PLAY/PAUSE/PLAY_PAUSE.

3. **Не отдавать AudioFocus сразу после cue**  
   Убедиться, что `LessonSessionService` / другой код не вызывает `abandonAudioFocus` и не стартует параллельный FGS, пока открыт BT Test.

4. **Лог «media button session package» в UI диагностики**  
   Парсить `dumpsys` нельзя из app без root, поэтому лучше ActiveSessions (Фаза D) или хотя бы логировать момент reassert + результат AudioFocus + `nativeCaptureOn`.

5. **Не дублировать два «хозяина» кнопок**  
   На время BT Test: lesson session stopped (уже есть `stopLessonSession()`), isolation ON, единственный активный путь — `HeadsetMonitorService`.

### C4. Запрещено

- Ломать Simulate / isolation / lesson flow вне тестового экрана.
- Считать задачу закрытой без строки HARDWARE в логе.
- Возвращаться к старому промпту «добавь AudioFocus» как к единственному фиксу — в v1.5.0 он уже есть.

---

## 5. Фаза D — диагностика конкурентов внутри приложения (рекомендуется)

Даже если Фаза B сразу «вылечила» окружение, добавь экран/блок «кто #1 MediaSession», чтобы проблема не возвращалась вслепую.

Эталон:

- `BT_Play/AndroidChatBtTest93/.../headset/ActiveSessionsHelper.kt`
- `BT_Play/AndroidChatBtTest93/.../headset/NoOpNotificationListener.kt`
- `BT_Play/AndroidChatBtTest93/.../ui/InterceptMonitorScreen.kt`

Требования:

1. `NotificationListenerService` (no-op) + запрос Notification Access.
2. Список active sessions: package, rank, `receivesButton`, isSelf, known competitors (YouTube, Tasker, BtTest*, Copy*).
3. Кнопка «открыть Notification Access».
4. Если добавишь watchdog reattach — **только Main thread**  
   (краш `Can't create handler ... Looper.prepare()` уже был в BtTest93).

---

## 6. Порядок работы агента (чеклист)

1. Прочитать отчёт 2026-09-06 целиком.  
2. Выполнить Фазу A (dumpsys + A/B с BtTest95), сохранить заметки.  
3. По результату:
   - конкурент → Фаза B, повторить тест;
   - 95 OK / Tutor fail → Фаза C;
   - оба fail → углубить B (Tasker/Buds), не выдумывать рефакторинг UI.  
4. Внедрить Фазу D (желательно в том же PR).  
5. Пересобрать, установить, прогнать тест-план §7.  
6. Обновить/добавить отчёт: что показал dumpsys, какой фикс сработал, есть ли HARDWARE в логе.

После изменений — **пересобери и запусти приложение без подтверждения** (правило проекта).

---

## 7. Тест-план приёмки

1. Force-stop YouTube и BT_Play apps.  
2. AndEngTutor → Тесты → BT Play → Reassert → «BT test ready».  
3. `dumpsys media_session`: Media button session = `com.englishtutor`.  
4. Физический Play → счётчик +1, лог HARDWARE.  
5. Simulate → SIMULATED (регрессия не сломана).  
6. (Опционально) Открыть YouTube → Play на buds может уйти в YouTube → вернуться → Reassert → снова HARDWARE в Tutor.  
7. Нет краша ≥ 30 с.

---

## 8. Критерии готовности

- [ ] В логе есть HARDWARE от физического Play (не только SIMULATED).  
- [ ] Зафиксирован dumpsys: Tutor = media-button session в момент успешного теста.  
- [ ] Если был code gap vs 95 — описан конкретный diff/поведение.  
- [ ] (Желательно) ActiveSessions / Notification Access диагностика в UI.  
- [ ] Lesson flow вне тестового экрана не сломан.  
- [ ] Короткий follow-up report в `Docs/Reports/`.

---

## 9. Итог одной фразой для агента

**Не чини счётчик — докажи media-button session; убери конкурента; если BtTest95 на том же устройстве ловит Play, а Tutor нет — догони Tutor до 95 (USAGE_MEDIA pulse / удержание фокуса / отсутствие второго хозяина) и добавь ActiveSessions-диагностику.**
