# Android English Tutor

Офлайн Android-приложение для изучения английского: уроки, стартовый тест, прогресс, управление через Bluetooth-гарнитуру.

**Текущая сборка:** v1.3.3 (versionCode 9)

## Возможности

- Уроки с несколькими фразами (Room + JSON в assets)
- Стартовый тест уровня (multiple choice + произношение)
- Урок в режиме eyes-free: `LessonSessionService` + `MediaSessionCompat`
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
│   ├── HeadsetTestService.kt      # Тест BT Play (как AndroidChat)
│   └── HeadsetTestController.kt   # Счётчик + журнал
├── ui/screens/voicetest/          # Окно тестов (3 вкладки)
└── data/supabase/                 # Отправка логов
```

## Документация

| Файл | Описание |
|------|----------|
| [Docs/cursor_prompt_language_tutor_mvp.md](Docs/cursor_prompt_language_tutor_mvp.md) | Исходный MVP-промпт |
| [Docs/headset-testing.md](Docs/headset-testing.md) | Тест кнопок гарнитуры |
| [Docs/AndroidChat-BT-Headset-Testing-Report.md](Docs/AndroidChat-BT-Headset-Testing-Report.md) | Сравнение с AndroidChat |
