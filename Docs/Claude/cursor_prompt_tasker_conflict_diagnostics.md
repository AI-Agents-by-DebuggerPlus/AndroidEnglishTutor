# Промпт для Cursor: диагностика конфликта с Tasker BT Key (Grab) в BT Play

## Контекст

Репозиторий `AndroidEnglishTutor`, `master` после коммитов `461cd7a` → `ed4dfff` → `801848f` (v1.4.1). Все предыдущие правки внутри приложения проверены и применены корректно:

- `HeadsetMonitorService` перезапускается при входе на Tests и на урок (не только один раз в `Application.onCreate()`).
- Статус «Native capture» подтверждается из самого сервиса (`attachMediaSession()` / `onDestroy()`), а не декларируется заранее.
- `PlaybackStateCompat.STATE_PAUSED` — как в рабочем AndroidChat (`801848f` откатил ошибочный `STATE_PLAYING` из `ed4dfff` — это верно, трогать не нужно).
- Логи отфильтрованы по уровню, дублирующий 2-секундный poll убран.

Прямое построчное сравнение `HeadsetMonitorService`/`HeadsetButtonNotifier` с рабочим `AndroidChat` (репозиторий `TaskerToWpf`) показывает, что код теперь практически идентичен. Дальнейшие правки внутри `com.englishtutor.session.*` низковероятны как причина.

**Новая находка** — не в коде AndroidEnglishTutor, а в собственной Tasker-документации проекта: `TaskerToWpf/Docs/MD_Files/Tasker-BT-Key-Policy.md`.

> Профиль **BT Key** (State **Media Button**, **Grab: On**, **Stop Event: On**) перехватывает media-кнопки гарнитуры. Пока профиль **ON** — Play/Pause не доходят до YouTube, Spotify, Music Player и других приложений. Подтверждено на Pixel 6a: BT Key OFF → Play на гарнитуре снова управляет медиа.

Это системный перехват на уровне Android (`Grab` в Tasker блокирует доставку media-кнопки любому другому слушателю `MediaSession`), **не специфичный для конкретного приложения**. Если Tasker-профиль с Grab включён на устройстве во время теста — свежепочиненный `HeadsetMonitorService` в AndroidEnglishTutor физически не может получить кнопку, сколько бы правок в его коде ни было сделано. Это наиболее вероятная причина, почему BT Play всё ещё «не работает» после двух раундов внутренних фиксов.

Задача этого промпта — не чинить `HeadsetMonitorService` (он уже в порядке), а **сделать этот внешний конфликт видимым внутри приложения**, чтобы не тратить время на дальнейшую отладку кода вслепую.

---

## Задача 1 — runtime-проверка разрешения Tasker на перехват media-кнопок

`PackageManager.checkPermission(permission, packageName)` — публичный метод, не требует специальных прав для проверки **чужого** разрешения. Используй его, чтобы узнать, выдано ли Tasker разрешение `android.permission.SET_MEDIA_KEY_LISTENER` (без этого разрешения Tasker физически не может грабить кнопки — это необходимое предусловие для конфликта, хотя само по себе не доказывает, что профиль сейчас ON).

1. Создай новый файл `app/src/main/java/com/englishtutor/bluetooth/TaskerConflictChecker.kt`:

```kotlin
package com.englishtutor.bluetooth

import android.content.Context
import android.content.pm.PackageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskerConflictChecker @Inject constructor() {

    /** true, если у Tasker есть разрешение слушать media-кнопки (необходимое условие для Grab-конфликта). */
    fun taskerMayGrabMediaButtons(context: Context): Boolean {
        return runCatching {
            context.packageManager.checkPermission(
                MEDIA_KEY_LISTENER_PERMISSION,
                TASKER_PACKAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    companion object {
        private const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
        private const val MEDIA_KEY_LISTENER_PERMISSION = "android.permission.SET_MEDIA_KEY_LISTENER"
    }
}
```

2. Внедри `TaskerConflictChecker` в `VoiceTestViewModel` через Hilt (`@Inject`), вызови `taskerMayGrabMediaButtons(appContext)` при входе в `enterHeadsetIsolation()` и положи результат в `VoiceTestUiState` новым полем:

```kotlin
val taskerMayConflict: Boolean = false,
```

3. На вкладке **BT Play** (`BtPlayTestSection` в `VoiceTestScreen.kt`) добавь предупреждение, если `taskerMayConflict == true`, сразу под статусом «Native capture: ON»:

```kotlin
if (taskerMayConflict) {
    Text(
        text = "У Tasker есть разрешение на перехват media-кнопок (SET_MEDIA_KEY_LISTENER). " +
            "Если включён профиль с Grab (например «BT Key») — кнопка гарнитуры не дойдёт до этого экрана. " +
            "Отключите профиль в Tasker на время теста.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
```

   Добавь параметр `taskerMayConflict: Boolean` в сигнатуру `BtPlayTestSection` и прокинь из `VoiceTestScreen`.

Это не устраняет конфликт (Tasker — сторонее приложение, программно выключить его профиль нельзя без Tasker-плагинов/intents, что выходит за рамки этой задачи), но переводит диагностику из «непонятно, почему не работает» в конкретное действие для пользователя.

---

## Задача 2 — явный лог при подозрении на конфликт

В `HeadsetTestController.setCaptureStatus()` (или рядом, в месте, куда стекается статус экрана Tests) добавь одноразовый `logger.w(...)`, если `taskerMayConflict == true` в момент открытия вкладки BT Play — чтобы это было видно и в логах, а не только в UI (полезно, если пользователь тестирует удалённо и присылает только лог-выгрузку, а не скриншот экрана).

Место вызова — там же, где сейчас вызывается `refreshIsolatedHandler()` в `VoiceTestViewModel.enterHeadsetIsolation()`:

```kotlin
if (taskerConflictChecker.taskerMayGrabMediaButtons(appContext)) {
    logger.w(TAG, "Tasker holds SET_MEDIA_KEY_LISTENER — possible Grab conflict with HeadsetMonitorService")
}
```

---

## Задача 3 — обновить чеклист приёмки

В `Docs/AndroidEnglishTutor-Bluetooth-Headset-Report-From-AndroidChat.md`, §10.4, замени пункт про Tasker BT Key на более точный, ссылающийся на новую runtime-проверку:

```
- [ ] На вкладке BT Play не показывается предупреждение о конфликте с Tasker (`taskerMayConflict = false`), ЛИБО профиль с Grab в Tasker выключен вручную перед тестом
```

---

## Не делать в этом промпте

- Не трогай `PlaybackStateCompat` — уже верно (`STATE_PAUSED`).
- Не меняй логику перезапуска `HeadsetMonitorService` — уже соответствует AndroidChat.
- Не пытайся программно выключать Tasker-профиль из кода AndroidEnglishTutor (нет легитимного публичного API для управления чужим приложением) — только диагностика и явное предупреждение пользователю.

---

## Критерии готовности

- [ ] На вкладке BT Play видно предупреждение, если у Tasker есть разрешение `SET_MEDIA_KEY_LISTENER`.
- [ ] При открытии Tests с подозрением на конфликт в логе (INFO/WARN, не DEBUG) есть явная запись про Tasker.
- [ ] После ручного выключения профиля Tasker с Grab (или отзыва `SET_MEDIA_KEY_LISTENER` через `adb shell pm revoke net.dinglisch.android.taskerm android.permission.SET_MEDIA_KEY_LISTENER`) и повторного нажатия Play на Pixel Buds Pro 2 — счётчик на BT Play растёт. Если и это не помогает — впервые за всю переписку это будет сигналом, что причина всё-таки внутри `HeadsetMonitorService`, а не внешняя, и тогда потребуется `adb shell dumpsys media_session` в момент нажатия для следующего раунда диагностики.
