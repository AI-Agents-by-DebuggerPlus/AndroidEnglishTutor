# AET ↔ Hermes через Telegram (как AHCC)

**Версия приложения:** 1.6.40+  
**Профиль агента:** `english_tutor`  
**Бот:** `@GoraAcademyFirstBot` (из `default_settings.json`)

## Зачем

Миничат **EnglishTutor** в AET запрашивает у Hermes offline study packs (JSON тем) и сохраняет их в Word Study. Канал тот же, что у AHCC: **пользователь TDLib → бот Telegram → Hermes**.

**Nous Inference отключён** — без успешного входа в Telegram миничат к агенту не ходит.

## Как это устроено

| Слой | Что |
|------|-----|
| TDLib в AET | Локальная сессия пользователя в `files/tdlib-aet/` (отдельно от AHCC / Desktop) |
| Telegram | Сообщения в личный чат с `@GoraAcademyFirstBot` от вашего аккаунта |
| Hermes | Читает бота (`getUpdates`), отвечает в тот же чат |
| Маршрутизация | Исходящий текст с префиксом `TO: english_tutor` |

Каждый клиент (AET, AHCC Android, AHCC Desktop) держит **свой** TDLib-логин. Полная синхронизация лент между клиентами **не требуется**: у каждого свой UI-канал к одному боту.

Сброс сеанса в AET **не** сбрасывает AHCC / Desktop.

## Настройки

Файл (локально, в `.gitignore`): `app/src/main/assets/default_settings.json`  
Образец: `default_settings.json.example`

Нужные поля:

- `telegramBotUsername` — username бота Hermes  
- `telegramApiId` / `telegramApiHash` — с [my.telegram.org/apps](https://my.telegram.org/apps)  
- `telegramPhone` — номер аккаунта для входа  
- `transport` — `telegram` (Inference не используется)

Скопируйте example → `default_settings.json` и заполните секреты. Не коммитьте `default_settings.json`.

## Подключение (пользователь)

1. Home → **EnglishTutor** (миничат).
2. Войдите в Telegram (телефон + код, или QR).  
   Код — в служебном чате **«Telegram»**, не SMS и не чат бота.  
   Подробно: [AET-Telegram-Login-Code.md](./AET-Telegram-Login-Code.md).
3. Индикатор: оранжевый **Telegram Ready**.
4. Кнопка **«Тест english_tutor (Telegram → Hermes)»** — пинг с `TO: english_tutor`.
5. Зелёный **english_tutor подключён** — реальный ответ профиля.
6. Чипы тем / сообщения → JSON → **Сохранить offline** → Word Study.

## Индикация `TutorLinkState`

| Состояние | Смысл |
|-----------|--------|
| TelegramOffline | TDLib не Ready |
| TelegramReady | Вошли в Telegram, профиль не проверен |
| Probing | Идёт тест |
| EnglishTutorOk | Ответ похож на EnglishTutor |
| EnglishTutorFailed | Telegram есть, профиль не подтвердился |

Тег logcat отправки: `AET-Hermes`. Auth TDLib: `AET-TG-User`.

## Код (ориентиры)

- `data/telegram/AetTelegramUserSession.kt` — TDLib user session  
- `data/tutor/EnglishTutorAgentClient.kt` — `TO: english_tutor` + wait reply  
- `ui/screens/tutorchat/` — миничат, баннер связи, тест  
- `EnglishTutorApp` — restore TDLib при старте  

## Offline темы

Ответ агента: один fenced JSON (`id`, `titleRu`, `words`, `phrases`, `sentences`) по протоколу EnglishTutor SOUL.  
Сохранение: `files/topics/<id>.json` через `TopicRepository.saveGenerated`.

## Связанные документы

- [AET-Telegram-Login-Code.md](./AET-Telegram-Login-Code.md) — код не приходит  
- Hermes (репозиторий NousPortalCloudHermes): `Docs/Agents/EnglishTutor.SOUL.md`, guides по multi-agent routing  
