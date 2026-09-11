# AndEngTutor (Android English Tutor)

Офлайн Android-приложение для изучения английского: уроки, стартовый тест, прогресс, управление через Bluetooth-гарнитуру.

**Отображаемое имя:** AndEngTutor (иконка с буквой **E**)  
**Пакет:** `com.englishtutor`  
**Текущая сборка:** v1.5.6 (versionCode 27)

## Возможности

- Уроки с несколькими фразами (Room + JSON в assets)
- Стартовый тест уровня (multiple choice + произношение)
- Урок eyes-free: `LessonSessionService` + `EnglishTutorPlayHandler` (pause/resume TTS, STT + SCO)
- Определение BT-гарнитуры, ACL-анонсы, кнопка Stop / закрытие приложения
- **Окно тестов** (TTS / STT / BT Play) по образцу BT_Play / AndroidChatBtTest95
- **BT Play:** HARDWARE/SIMULATED, Play/Next, debounce + интервал Next, Reassert, USAGE_MEDIA pulse, диагностика ActiveSessions
- **Экран логов** → Supabase (`[LOG:category] message`)

## Окно тестов → BT Play

1. Главная → **Окно тестов**
2. Вкладка **BT Play** (по умолчанию) — reassert + cue *«BT test ready»* + pulse
3. Одиночный Play → счётчик **Play** + `[HARDWARE]`
4. Двойной жест (Next на Buds) → счётчик **Next** (companion Play подавляется)
5. **Симулировать Play** → `[SIMULATED]`
6. После YouTube / другого плеера → **Reassert MediaSession**

Перед тестом закройте YouTube и другие media-приложения.

Подробнее: [Docs/headset-testing.md](Docs/headset-testing.md)  
Порт / проблемы и решения: [Docs/Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md](Docs/Reports/AndEngTutor_BT_Play_Problems_and_Porting_Guide_2026-09-10.md)  
Отчёты: [Docs/Reports/](Docs/Reports/)

## Настройка Supabase (логи)

1. Скопируйте `app/src/main/assets/default_settings.json.example` → `default_settings.json`
2. Заполните `supabaseUrl` и `supabaseAnonKey`
3. Файл `default_settings.json` в `.gitignore` — не коммитить

## Сборка

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME = "D:\gradle-home"
cd AndroidEnglishTutor
.\gradlew.bat assembleDebug installDebug
adb shell am start -n com.englishtutor/.MainActivity
```

## Структура

```
app/src/main/java/com/englishtutor/
├── session/
│   ├── HeadsetMonitorService.kt   # FGS + MediaSession + AudioFocus + pulse
│   ├── MediaPlaybackPulse.kt      # USAGE_MEDIA claim (UID приложения)
│   ├── HeadsetButtonNotifier.kt   # Debounce, Next, suppress, isolation
│   ├── HeadsetButtonPreferences.kt # Debounce / Next interval prefs
│   ├── HeadsetTestController.kt   # Счётчики Play/Next + журнал
│   ├── EnglishTutorPlayHandler.kt # BT Play → урок
│   └── LessonSessionService.kt    # Урок FGS (audio focus для TTS/STT)
├── bluetooth/                     # Devices, ACL, diagnostics, ActiveSessions
├── ui/screens/voicetest/          # Окно тестов
└── data/supabase/                 # Отправка логов
```

## Скрипты

- `Scripts/HibernateCountdown.ps1` — гибернация через 10 с с кнопкой Cancel
- `Scripts/ShutdownCountdown.ps1` — выключение через 10 с с кнопкой Cancel
