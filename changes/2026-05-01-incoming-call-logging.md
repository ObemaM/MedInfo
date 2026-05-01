# 2026-05-01: Логирование входящих вызовов

## Кратко

Добавлено единое логирование потока госпитализаций, чтобы в logcat можно было отследить путь вызова от SignalR/серверного списка до локальной очереди и экрана принятия решения.

## Изменения

- Добавлен `CallLog` с единым тегом `CALL_LOG`.
- SignalR логирует получение `HospitalizationNotification`, каждую госпитализацию, необходимость решения и запуск fullscreen-экрана.
- SignalR логирует `MessageNotification`, потому что сообщение от бригады влияет на таймер принятия решения.
- `CallsManager` логирует добавление, обновление, удаление вызова, синхронизацию с серверным списком и старт/коррекцию таймера.
- `MainViewModel` логирует загрузку серверной страницы госпитализаций.
- `IncomingCallActivity` логирует получение вызова через intent и показ деталей.

## Затронутые файлы

- `app/src/main/java/com/example/medinfo/util/CallLog.kt`
- `app/src/main/java/com/example/medinfo/data/signalr/SignalRService.kt`
- `app/src/main/java/com/example/medinfo/data/manager/CallsManager.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallActivity.kt`

## Проверка

- `java -jar .\gradle\wrapper\gradle-wrapper.jar :app:assembleDebug :app:lintDebug` - успешно.

## Заметки

- В логи не пишем ФИО, адрес, повод вызова и медицинские детали пациента. Для отладки оставлены id госпитализации, номер вызова, статус, решение, времена и состояние уведомления.
- Смотреть поток можно через фильтр logcat по тегу `CALL_LOG`.
