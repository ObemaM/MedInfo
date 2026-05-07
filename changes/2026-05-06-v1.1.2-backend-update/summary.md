# 2026-05-06: Обновление под backend v1.1.2

## Кратко

Сверил Android-клиент с материалами `Lpu.Informator v1.1.2`, Word-спецификацией и DTO бэкенда. Внес точечные правки под новый контракт: `creationTime`, nullable-поля состояния пациента и новый порядок сообщений.

## Изменения

- Добавлено поле `creationTime` в `HospitalizationResponseDto` и UI-модель `Hospitalization`.
- Локальная очередь входящих решений в `CallsManager` теперь сортируется по `creationTime` с fallback на `call.callTime`.
- Основные списки `ACTIVE` и `ARCHIVE` в `MainViewModel` сортируются по `creationTime`; список `REQUIRES_DECISION` использует `creationTime` как tie-break после таймера решения.
- Legacy-конвертер `CallNotificationDto -> HospitalizationResponseDto` заполняет `creationTime` из старого `callTime`, чтобы тестовые/старые сценарии продолжали компилироваться.
- Поля `PatientConditionResponseDto.startDisease`, `vozr`, `mrs` сделаны nullable.
- `ChatActivity` больше не пересортировывает `get-messages` локально и показывает сообщения в порядке сервера: от старых к новым.
- В экранах деталей и входящего решения добавлено отображение времени создания госпитализации.
- Диагностический `CallLog` теперь пишет `creationTime`.

## Затронутые файлы

- `app/src/main/java/com/example/medinfo/model/api/HospitalizationListDtos.kt`
- `app/src/main/java/com/example/medinfo/model/api/MessageDtos.kt`
- `app/src/main/java/com/example/medinfo/model/Hospitalization.kt`
- `app/src/main/java/com/example/medinfo/data/manager/CallsManager.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`
- `app/src/main/java/com/example/medinfo/ui/chat/ChatActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/details/HospitalizationDetailsActivity.kt`
- `app/src/main/java/com/example/medinfo/ui/incoming/IncomingCallActivity.kt`
- `app/src/main/java/com/example/medinfo/util/CallLog.kt`

## Проверка

- `.\gradlew.bat :app:compileDebugKotlin` — успешно.
- `git diff --check` — успешно, только существующие предупреждения Git о будущей нормализации LF -> CRLF.

## Заметки

- `skills-lock.json` уже был изменен до этой работы, я его не трогал.
- Старые endpoints `get-calls` / `answer-call` все еще остаются в проекте как legacy-код, но в рамках v1.1.2 я их не удалял, чтобы не расширять область задачи без отдельной проверки сценариев.
