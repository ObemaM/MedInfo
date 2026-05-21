# 2026-05-21: Realtime-списки и фильтры новой спецификации

## Кратко

Список вызовов стал обновляться от SignalR без переключения вкладок, открытые экраны вызова теперь принимают обновления статуса на лету. Фильтр списка подготовлен под поля из новой спецификации v1.1.5.

## Изменения

- Добавлена общая шина состояния SignalR-подключения и индикатор связи в шапке главного экрана.
- `MainViewModel` слушает realtime-обновления госпитализаций и точечно добавляет/обновляет вызовы в текущем списке.
- В режиме принятия решения по данным пациента локальная вкладка "Требуют решения" теперь сверяется с очередью `CallsManager`, чтобы новый активный вызов не попадал туда раньше сообщения с данными пациента.
- `HospitalizationDetailsActivity` и `IncomingCallActivity` подписаны на обновления госпитализации и перебиндивают полные данные, статус, решение и времена без выхода с экрана.
- В фильтр добавлены поля новой спецификации: ФИО пациента, дата начала госпитализации от/до, дневной номер, годовой номер.
- Дневной и годовой номер в UI фильтра теперь трактуются как точное значение, а не диапазон.
- Кнопка отправки в чате оставлена как иконка отправки без текста.
- Для пустых вкладок добавлены плейсхолдеры: нет активных вызовов, нет вызовов для решения, архив пуст.
- Очередь `CallsManager` теперь сортируется по дедлайну решения, поэтому всплывающий экран и вкладка "Требуют решения" показывают самый срочный вызов одинаково.
- Debug-кнопки симуляции сообщений на экране решения теперь появляются только для тестовых вызовов, а не для любого реального вызова при включенном `testCallEnabled`.
- Для тестового вызова добавлены кнопки смены статуса госпитализации: "Бригада в пути", "Бригада на месте", "Завершена", "Передана в другое ЛПУ". Они имитируют `HospitalizationNotification` через `HospitalizationEventBus` и синхронизируют локальную очередь `CallsManager`.
- На экране логина добавлен индикатор загрузки и блокировка полей на время сетевого запроса.

## Затронутые файлы

- `app/src/main/java/com/example/medinfo/data/manager/SignalRConnectionState.kt`
- `app/src/main/java/com/example/medinfo/data/manager/CallsManager.kt`
- `app/src/main/java/com/example/medinfo/data/signalr/SignalRService.kt`
- `app/src/main/java/com/example/medinfo/model/api/HospitalizationListDtos.kt`
- `app/src/main/java/com/example/medinfo/receiver/FakeCallAlarmReceiver.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/main/CallFilters.kt`
- `app/src/main/java/com/example/medinfo/ui/main/CallFiltersDialogFragment.kt`
- `app/src/main/java/com/example/medinfo/ui/login/LoginActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/details/HospitalizationDetailsActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallActivity.kt`
- `app/src/main/java/com/example/medinfo/util/DateFormatter.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_chat.xml`
- `app/src/main/res/layout/activity_incoming_call.xml`
- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/layout/dialog_call_filters.xml`
- `app/src/main/res/drawable/connection_status_dot.xml`

## Проверка

- `java -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -jar .\gradle\wrapper\gradle-wrapper.jar --no-daemon :app:assembleDebug` - успешно.
- `git diff --check` - без ошибок, только стандартные предупреждения Git про CRLF.

## Что проверить дальше

- На реальном сервере проверить сценарий: вызов сначала появляется в "Активные", затем после сообщения с данными пациента появляется в "Требуют решения".
- Проверить, что активный вызов появляется в текущей вкладке без ручного переключения вкладок.
- Проверить, что при смене статуса на сервере карточка и раскрытые полные данные обновляются, если врач уже находится внутри вызова.
- Без серверной отправлялки можно включить `testCallEnabled`, открыть тестовый вызов и нажать debug-кнопки статусов. Активные статусы должны обновить карточку на месте, архивные статусы должны убрать вызов из очереди решений.
- Согласовать с серверной командой точный формат `HospitalizationDateTimeFrom/To`: сейчас отправляется локальное время в формате `yyyy-MM-dd'T'HH:mm:ss`.
