# XSHOOT

Отдельное Windows-приложение по мотивам «тира» Bridge To English: партия слов → режим обучения → тир (русский → английский). Отгаданные слова пишутся в **архив**.

## Запуск

```powershell
dotnet run --project Desktop.XShoot
```

Или опубликованный exe:

`Desktop.XShoot\publish\AETXShoot.exe`

Словарь собирается из слов уроков AET:

```powershell
python Scripts\build_xshoot_dictionary.py
```

## Архив

`%AppData%\AETXShoot\guessed_archive.json`

При сохранении копия также пишется в:

- `Files/xshoot_archive.json`
- `app/src/main/assets/xshoot_archive.json` (если рядом исходники AET)

На телефоне архив читается из assets и из `filesDir/xshoot_archive.json`. Слова из архива **не показываются** как отдельные карточки «слова», но могут оставаться в словосочетаниях и предложениях.

Генераторы уроков могут вызывать `Scripts/xshoot_archive.py` (`filter_words`).

## Режимы

- **Упражнения** — словарь, статистика архива, новая партия.
- **Обучение** — список партии и перевод выбранного слова.
- **Тир** — на табло русский перевод, ввод английского слова, таймер, пропуск.
