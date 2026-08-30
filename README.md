# Android English Tutor

Офлайн Android-приложение для изучения английского: уроки, стартовый тест, прогресс, управление через Bluetooth-гарнитуру.

**Текущая сборка:** v1.3.8 (versionCode 14)

## Возможности

- Уроки с несколькими фразами (Room + JSON в assets)
- Стартовый тест уровня (multiple choice + произношение)
- Урок в режиме eyes-free: `LessonSessionService` + `EnglishTutorPlayHandler` (pause/resume TTS, STT + SCO)
- Определение BT-гарнитуры, голосовое приветствие, кнопка Stop
- **Окно тестов** (TTS / STT / BT Play) — по образцу AndroidChat
- **Экран логов** с отправкой на Supabase (`[LOG:category] message`)
- Версия сборки на главных экранах

## Окно тестов → вкладка BT Play

Как в [AndroidChat](Docs/AndroidChat-BT-Headset-Testing-Report.md):

1. Главная → **Окно тестов (речь + гарнитура)**
2. Вкладка **BT Play** — сервис стартует автоматически
3. Уведомление «BT Play (English Tutor)» — MediaSession активен
4. Нажмите Play на гарнитуре или **Симулировать Play**
5. Счётчик и журнал событий обновляются без TTS/STT

Подробнее: [Docs/headset-testing.md](Docs/headset-testing.md)

## Настройка Supabase (логи)

1. Скопируйте `app/src/main/assets/default_settings.json.example` → `default_settings.json`
2. Заполните `supabaseUrl` и `supabaseAnonKey`
3. Файл `default_settings.json` в `.gitignore` — не коммитить

Протокол: `TaskerToWpf/Docs/MD_Files/AndroidEnglishTutor-Supabase-Logs-Protocol.md`

## Сборка

```powershell
$env:GRADLE_USER_HOME = "D:\gradle-home"
cd AndroidEnglishTutor
.\gradlew.bat assembleDebug installDebug
```

Package: `com.englishtutor`

## Структура

```
app/src/main/java/com/englishtutor/
├── session/
│   ├── LessonSessionService.kt    # Урок: MediaSession + audio focus
│   ├── EnglishTutorPlayHandler.kt # BT Play → pause/resume/STT/next
│   ├── HeadsetMonitorService.kt   # Sticky MediaSession (как AndroidChat)
│   ├── HeadsetButtonNotifier.kt # Маршрутизация + isolation
│   └── HeadsetTestController.kt # Счётчик + журнал BT Play
├── bluetooth/                     # BT device detection, SCO, ACL monitor
├── ui/screens/voicetest/          # Окно тестов (3 вкладки)
└── data/supabase/                 # Отправка логов
```

## Документация

| Файл | Описание |
|------|----------|
| [Docs/cursor_prompt_language_tutor_mvp.md](Docs/cursor_prompt_language_tutor_mvp.md) | Исходный MVP-промпт |
| [Docs/headset-testing.md](Docs/headset-testing.md) | Тест кнопок гарнитуры |
| [Docs/AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md](Docs/AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md) | Полный отчёт по BT из AndroidChat |
| [Docs/AndroidChat-BT-Headset-Testing-Report.md](Docs/AndroidChat-BT-Headset-Testing-Report.md) | Сравнение с AndroidChat |
