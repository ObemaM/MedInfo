# 2026-05-25: Очистка фильтров и индикатор связи

## Кратко

Убраны legacy-фильтры, которые больше не поддерживаются новой спецификацией поиска. Индикатор отсутствия связи в шапке сделан заметнее.

## Изменения

- Из диалога фильтров убраны поля срочности, пола и возраста.
- В модели `CallFilters` оставлены только актуальные фильтры: дата начала госпитализации, дневной номер и годовой номер.
- Локальная проверка фильтров теперь сверяет только актуальные поля.
- Плейсхолдер строки поиска изменен с "Поиск" на "ФИО", так как запрос отправляется на сервер как `PatientFullName`.
- При потере/восстановлении связи с сервером иконка информации в шапке теперь тоже меняет цвет: красная при отсутствии связи, желтая при подключении, обычная при подключенном состоянии.

## Затронутые файлы

- `app/src/main/java/com/example/medinfo/ui/main/CallFilters.kt`
- `app/src/main/java/com/example/medinfo/ui/main/CallFiltersDialogFragment.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/dialog_call_filters.xml`

## Проверка

- `java -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -jar .\gradle\wrapper\gradle-wrapper.jar --no-daemon :app:assembleDebug` - успешно.
- `git diff --check` - без ошибок, только предупреждения Git про CRLF.
