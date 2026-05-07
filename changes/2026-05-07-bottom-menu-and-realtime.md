# 2026-05-07: Bottom-menu redesign, in-app call alert, realtime chat

## Кратко

Большой набор связанных изменений поверх коммита `93e1741`: переделана нижняя
менюшка главного экрана (TabLayout → карточка + popup со счётчиком badge), внедрён
in-app алерт для входящего вызова, когда врач уже на экране другого вызова,
переписан `IncomingCallRinger` (без auto-stop), добавлен realtime для чата через
SharedFlow-шину, мелкие правки сортировки и моделей под новое поле `creationTime`
от бэкенда.

## Изменения

### Нижнее меню главного экрана

- `activity_main.xml`: TabLayout заменён на карточку `tab_menu_button` с
  выбранным фильтром и иконкой-«бургером». Внутри текст вкладки + красный
  badge со счётчиком «Требуют решения».
- `popup_tab_menu.xml` (новый): popup со списком из трёх пунктов; рядом с
  «Требуют решения» — такой же badge.
- `MainActivity.kt`:
  - `setupTabsListener` (`TabLayout`) → `setupTabMenuListener` + `showTabMenuPopup`.
  - `bindTabMenuItem` рисует фон с правильно скруглёнными углами для первого/
    последнего пункта (см. комментарий в `createTabItemBackground`).
  - Подписка на `CallsManager.calls` для счётчика, который не зависит от
    активной вкладки и поиска.
  - Хелпер `getDecisionCountText`: если очередь достигла `pageSize=40` —
    показываем «39+» (`pageSize - 1` + `+`).
  - `getTabAccent` / `createTabMarker` оставлены, но сейчас не используются —
    зарезервированы под цветные точки в popup.
- Цвет выделения вкладки в popup'е: добавлен `tab_selected_bg = #DEE3EE` в
  `colors.xml` (компромисс между `blue_3` и `blue_2`).
- `badge_circle.xml` (новый), `ic_menu_24.xml` (новый), строка
  `menu_requires_decision`, plurals `calls_count`.

### In-app алерт о входящем вызове (поверх IncomingCallActivity)

- `AppVisibilityTracker` (новый, `util/`): отслеживает текущую foreground-активити
  через `ActivityLifecycleCallbacks`; регистрируется в `MedInfoApplication.onCreate`.
- `InAppIncomingCallAlert` (новый, `ui/incoming/`): `PopupWindow` сверху экрана
  с заголовком + подзаголовком (ФИО + причина), swipe-to-dismiss, тап = открыть
  IncomingCallActivity для нового вызова.
- `view_in_app_call_alert.xml`, `in_app_call_alert_background.xml` (новые):
  разметка и фон (карточка с красной обводкой). Ширина = ширина экрана минус
  16dp с каждой стороны (как у системного heads-up). Высота ~100dp (paddings
  22dp, gap 8dp). Шрифт 17sp/15sp.
- `dimens.xml`: `call_alert_horizontal_margin`, `call_alert_top_margin`,
  `call_alert_elevation`.
- `SignalRService.alertIncomingDecisionCall`: маршрутизирует входящий вызов —
  в foreground на `IncomingCallActivity` показываем in-app алерт + brief-звон,
  иначе старый `triggerFullscreenAlert` с continuous-звоном.

### Звонок-ringer

- `IncomingCallRinger`:
  - Удалён `Handler` + `stopRunnable` (auto-stop через `postDelayed`).
  - Добавлен флаг `isBriefMode` — ставится только если `startBrief` фактически
    запустил звон.
  - Метод `start()` (continuous) сбрасывает `isBriefMode` и не цепляет таймер.
  - Метод `startBrief()` теперь без таймера: если `isRunning` — просто выходит,
    иначе запускает звук и помечает `isBriefMode=true`.
  - Новый метод `stopBrief()`: глушит звон только если был запущен brief'ом.
  - В `InAppIncomingCallAlert` к `PopupWindow` навешен
    `setOnDismissListener { IncomingCallRinger.stopBrief() }` — при свайпе или
    программном dismiss звук останавливается, не задевая continuous-звон
    основного вызова.

Это закрывает баг с заглушением звона основного вызова при приходе нового
(brief заглушал continuous через 5с) и проблему «звук пропадает через 5с» в
сценариях, когда `startBrief` оказывался единственным источником звона.

### Realtime для чата

- `MessagesEventBus` (новый, `data/manager/`): синглтон с
  `MutableSharedFlow<MessageResponseDto>` (replay=0, extraBufferCapacity=16).
- `SignalRService.handleMessageNotifications`: пушит каждое сообщение в шину
  (`MessagesEventBus.emit(it)`), помимо существующей логики таймеров и
  confirm-reception.
- `ChatActivity`:
  - Поле `shownMessageIds` для дедупа.
  - Загрузка истории + подписка на шину обёрнуты в `repeatOnLifecycle(STARTED)` —
    при возврате на экран список рефрешится, потом начинается приём live событий.
  - `loadMessages` стал тонкой обёрткой над suspend `fetchAndRenderMessages`.
  - Новая `appendMessage(message)` — добавляет один пузырёк, обновляет set,
    скроллит вниз; убирает state-плейсхолдер «Сообщений пока нет», если был.
  - `scrollToBottom()` вынесен в отдельный метод.
  - Заменено `R.color.blue_2` на `R.color.blue_3` для своих сообщений (мягче).

ChatViewModel специально не вводился — оставили на отдельную задачу, чтобы
сохранить минимальный диф.

### Серверная модель + сортировка по `creationTime`

- `HospitalizationResponseDto`, `Hospitalization`: добавлено поле
  `creationTime: String?`.
- `CallsManager.upsertCall`: список сортируется по `creationTime`
  (fallback `call.callTime`), стабильно по `id`. Помечен `TODO` под перевод
  сортировки на «у кого таймер истекает быстрее».
- `MainViewModel.sortByCreationTimeNewestFirst` + `creationSortMillis`:
  для вкладок ACTIVE и ARCHIVE сортировка «свежие вверху», битые/пустые даты
  уезжают в хвост.
- `CallLog.hospitalization`: в выводе добавлен `creationTime`.
- `HospitalizationDetailsActivity`, `IncomingCallActivity`: показывают
  «Время создания госпитализации» в дебаг-секции.

### Прочие правки

- `CallsCache` удалён — кэш звонков больше не используется (вся очередь живёт
  в `CallsManager`). Из `MainViewModel` убраны вызовы `callsCache.clear(...)`
  при logout.
- `MainViewModel.companion object` сделан публичным, чтобы `MainActivity`
  мог читать `DEFAULT_PAGE_SIZE`.
- `PatientConditionResponseDto`: `startDisease`, `vozr`, `mrs` стали
  nullable — бэкенд иногда не присылает.
- Кнопки в `activity_login.xml` и `activity_config.xml` получили
  `cornerRadius=12dp`; кнопка «Закрыть» в config-экране красная (`red_1`).

## Затронутые файлы

### Новые

- `app/src/main/java/com/example/medinfo/data/manager/MessagesEventBus.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/InAppIncomingCallAlert.kt`
- `app/src/main/java/com/example/medinfo/util/AppVisibilityTracker.kt`
- `app/src/main/res/drawable/badge_circle.xml`
- `app/src/main/res/drawable/ic_menu_24.xml`
- `app/src/main/res/drawable/in_app_call_alert_background.xml`
- `app/src/main/res/layout/popup_tab_menu.xml`
- `app/src/main/res/layout/view_in_app_call_alert.xml`

### Удалённые

- `app/src/main/java/com/example/medinfo/data/cache/CallsCache.kt`

### Изменённые

- `app/src/main/java/com/example/medinfo/MedInfoApplication.kt`
- `app/src/main/java/com/example/medinfo/data/manager/CallsManager.kt`
- `app/src/main/java/com/example/medinfo/data/repository/HospitalizationRepository.kt`
- `app/src/main/java/com/example/medinfo/data/signalr/SignalRService.kt`
- `app/src/main/java/com/example/medinfo/model/Hospitalization.kt`
- `app/src/main/java/com/example/medinfo/model/api/HospitalizationListDtos.kt`
- `app/src/main/java/com/example/medinfo/model/api/MessageDtos.kt`
- `app/src/main/java/com/example/medinfo/ui/chat/ChatActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/details/HospitalizationDetailsActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallPermissionHelper.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallRinger.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`
- `app/src/main/java/com/example/medinfo/util/CallLog.kt`
- `app/src/main/res/layout/activity_config.xml`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`

## Code-review (важное и риски)

### Хорошее

- `MessagesEventBus` — минимальный паттерн pub/sub, аккуратно вписался в
  существующий `SignalRService`. Дедуп по `id` в `ChatActivity` страхует от
  дубликатов между API-фетчем и live-событиями.
- `IncomingCallRinger` без таймера упростил state-машину: остались только
  `isRunning` + `isBriefMode`, поведение предсказуемое. Логика «continuous
  перебивает brief» сделана через сброс флага в `start()`.
- `AppVisibilityTracker` через `ActivityLifecycleCallbacks` — стандартный
  способ, без бойлерплейта в активити.
- Сортировки выделены в отдельные методы с понятными именами.

### Что стоит держать в голове / возможные риски

1. **`tabMenuPopupWindow` — потенциальный leak при rotation.** Активити
   пересоздаётся, поле `tabMenuPopupWindow` указывает на старый popup из старой
   View. Для главного экрана сейчас фиксированная portrait-ориентация, но если
   когда-то расширите — добавьте `dismiss()` в `onPause`/`onDestroy`.
2. **`IncomingCallRinger.isRunning` десинк.** Если MediaPlayer внутри отвалится
   (отключили наушники, потерян audio focus), `isRunning` останется `true`,
   следующий `start()` вернётся ранним return и звон не перезапустится. Можно
   повесить `OnErrorListener` / `OnCompletionListener`, чтобы сбросить флаг.
3. **`MessagesEventBus` теряет события вне `STARTED`.** `replay=0`, поэтому
   сообщения, пришедшие, пока чат не на переднем плане, теряются. Это закрывается
   `fetchAndRenderMessages()` на возврате — но в окне между ним и началом
   `collect` теоретически возможен race. Вероятность мизерная, но если будут
   баги «не видно сообщения» — стоит вспомнить.
4. **ChatActivity всё ещё без ViewModel.** Сейчас ок, для testing достаточно.
   После того как realtime будет подтверждён двумя устройствами — стоит
   вынести в `ChatViewModel` по образцу `MainViewModel`.
5. **`PatientConditionResponseDto`: nullable-поля.** Сейчас бэкенд не всегда
   шлёт `startDisease`/`vozr`/`mrs`. UI готов (`condition.foo?.let { ... }`), но
   стоит уточнить у бэкенда, какие поля обязательны.
6. **`getTabAccent` / `createTabMarker` — мёртвый код.** Помечен комментарием
   «зарезервировано». Если в течение пары итераций цветные точки не вернутся —
   удалить.
7. **`InAppIncomingCallAlert.activePopupRef` — `WeakReference`.** Это правильно,
   но если несколько вызовов прилетят почти одновременно, второй dismiss'ит
   первый. Сейчас приемлемо, в UI остаётся последний — не теряется ничего
   критичного, потому что данные хранятся в `CallsManager`.

### Лёгкие nit'ы

- В `MainActivity` хелперы `dp`, `getTabTitle`, `getDecisionCountText` и т.д.
  можно вынести отдельно, но пока их немного — ок.
- `loadMessages()` в `ChatActivity` вызывается только после `sendMessage` —
  можно убрать, если бэкенд гарантирует SignalR-эхо отправителю. Сейчас
  оставлено как safety net.

## Проверка

- Главный экран:
  - [ ] нижняя плашка показывает «Требуют решения» + красный badge с числом
  - [ ] при ≥40 в очереди — badge показывает «39+»
  - [ ] клик по плашке открывает popup со всеми тремя пунктами; выбранный
        подсвечен `tab_selected_bg`, скруглён по углам карточки
  - [ ] переключение вкладки меняет список и обновляет заголовок плашки
- Входящий вызов:
  - [ ] вне приложения / на других экранах — открывается `IncomingCallActivity`
        на весь экран как раньше
  - [ ] на `IncomingCallActivity` (другой вызов в обработке) — появляется
        in-app алерт сверху с brief-звоном
  - [ ] свайп алерта → звон останавливается (если он был запущен brief'ом)
  - [ ] свайп алерта при играющем continuous → continuous продолжает играть
  - [ ] тап по алерту → переходит к новому вызову
- Чат:
  - [ ] открыли чат → история подгружена
  - [ ] партнёр отправил сообщение через SignalR → появляется без полного
        перерендера
  - [ ] своё отправленное сообщение приходит и не дублируется
  - [ ] сворачивание/разворачивание чата → история перезагружается
- Сортировка:
  - [ ] вкладки ACTIVE/ARCHIVE — свежие сверху
  - [ ] REQUIRES_DECISION — порядок по таймеру решения, тие-брейк по
        creationTime

## Заметки

- Канал входящих вызовов и `setBypassDnd(true)` были добавлены и затем
  откачены вместе с переходом обратно на `InAppIncomingCallAlert`. Если
  понадобится bypass DND — потребуется
  `ACCESS_NOTIFICATION_POLICY` в манифесте + ручное разрешение пользователем
  через системные настройки. Это сейчас НЕ сделано — намеренно, чтобы не
  тащить лишний permission.
- Канал нотификаций вообще для входящих вызовов сейчас не используется (всё
  делается через активити + in-app алерт).
- Файл `InAppIncomingCallAlert.kt` остался на диске — пока используется как
  основной механизм. Если вернёмся к системным нотификациям — можно будет
  удалить.
