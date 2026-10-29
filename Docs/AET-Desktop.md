# AET Desktop

Окно на компьютере повторяет экран **урока слов** телефона (тема, этап, английский, русский, чёрный экран).

Озвучка и кнопки Bluetooth-гарнитуры остаются только в Android AET. Desktop ничего не воспроизводит и команды не принимает.

## Как связано

Таблица: **`public.messages`**. Текст карточки лежит в колонке `content` (JSON после префикса), не в отдельной таблице уроков.

Телефон при каждом изменении карточки пишет в Supabase `messages` строку:

```text
[STUDY:Display]
{ ...json карточки... }
```

`sender_name` — как в `default_settings.json` (обычно `AndroidEnglishTutor`), `recipient_name` — `AETDesktop`.

Desktop опрашивает последнюю такую строку примерно раз в 0,7 с.

Нужны те же `supabaseUrl` и `supabaseAnonKey`, что у телефона (`app/src/main/assets/default_settings.json`). Анонимный ключ должен иметь право **читать** чужие строки `messages` с этим префиксом (как WpfChat читает логи). Если выборка пустая, в SQL:

```sql
create policy "aet_desktop_read_study_display"
on public.messages
for select
to anon, authenticated
using (content like '[STUDY:Display]%');
```

## Запуск

Окно — WPF (`Desktop.Wpf`). Двойной щелчок:

`Desktop.Wpf\publish\AETDesktop.exe`

Рядом должен лежать `default_settings.json` (копия из `app\src\main\assets`). В нём уже записаны `supabaseUrl` и `supabaseAnonKey`. Масштаб, пауза, яркость, цвета и отступ сверху лежат отдельно: `%AppData%\AETDesktop\desktop-settings.json`.

Установщик с программой, ключами и текущими локальными настройками:

`Desktop.Wpf\publish\AETDesktopSetup.exe`

При запуске закрывает старый AET Desktop, **удаляет** папку `%LocalAppData%\AETDesktop`, ставит новую версию, обновляет ярлык на рабочем столе, пишет `default_settings.json` рядом с exe и снимок локальных настроек в `%AppData%\AETDesktop`. Этот файл содержит ключи, его не нужно выкладывать в git и передавать посторонним. Собрать заново: `Scripts\build_desktop_setup.ps1`.

Собрать заново:

```powershell
cd D:\Programming\Cursor\2026\August\AndroidEnglishTutor
dotnet publish Desktop.Wpf\Desktop.Wpf.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o Desktop.Wpf\publish
Copy-Item app\src\main\assets\default_settings.json Desktop.Wpf\publish\default_settings.json
```

Запуск из исходников без exe:

```powershell
.\gradlew.bat -p desktop run
```

На телефоне откройте урок слов — то же появится в окне Desktop.

Окно без системной строки заголовка. В полном экране заголовка нет. **F** или **Escape** переключает полный экран и обычное окно. Тема, этап и кнопки окна стоят в одной нижней строке. Новая карточка с телефона прячет эту строку и курсор. Мышь или клавиша снова показывает карточку и нижнюю строку. Курсор скрывается через пять секунд бездействия. В локальных настройках есть отступ карточки сверху.

Раз в минуту Desktop проверяет Supabase и заново входит, если связь пропала. Пока окно открыто, компьютер не уходит в сон: состояние бодрствования обновляется каждые пять минут.

Мышь или клавиша снова показывает карточку после затемнения и возвращает яркость к текущему значению. Скрытый режим на телефоне держит экран телефона чёрным и сразу показывает ту же карточку здесь.

Кнопка настроек на Desktop задаёт паузу до затемнения, яркость всего окна и цвета английского и русского текста. Отдельный переключатель снижает яркость от текущего значения до нуля за время паузы. Эти значения и масштаб **Ctrl +/-** (50–300%) пишутся в `%AppData%\AETDesktop\desktop-settings.json` и сохраняются между запусками. Слева внизу — оставшиеся секунды локальной паузы.

Desktop можно запустить на другом компьютере: ему нужен интернет до того же проекта Supabase и тот же `default_settings.json` (`supabaseUrl`, `supabaseAnonKey`). Общая сеть с телефоном не нужна.
