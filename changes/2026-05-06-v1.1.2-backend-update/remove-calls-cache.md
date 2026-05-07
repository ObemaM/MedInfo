# 2026-05-06: Удаление legacy-кэша вызовов

## Кратко

Удален неиспользуемый файловый кэш вызовов `CallsCache`. После перехода на серверную пагинацию и `/hospitalizations/get-hospitalizations` список больше не должен восстанавливаться из локального JSON-кэша.

## Изменения

- Удален файл `app/src/main/java/com/example/medinfo/data/cache/CallsCache.kt`.
- Из `MainViewModel` убраны импорт `CallsCache`, поле `callsCache` и очистка кэша при logout.
- Оставлена очистка runtime-очереди `CallsManager.clearAll()` и JWT/session-данных при logout.

## Затронутые файлы

- `app/src/main/java/com/example/medinfo/data/cache/CallsCache.kt`
- `app/src/main/java/com/example/medinfo/ui/main/MainViewModel.kt`

## Проверка

- `rg "CallsCache|callsCache|data\\.cache|calls_cache_|cleanupOldCache|containsCallNumber|writeCalls|readCalls" app/src/main/java` — ссылок не найдено.
- `.\gradlew.bat :app:compileDebugKotlin` — успешно.

## Заметки

- Это только первый шаг удаления legacy. Старый `CallRepository`, endpoints `/api/informator/get-calls` / `/api/informator/answer-call`, `CallListContent`, `CallAnswerRequest`, `CallNotificationDto` и тестовые сценарии `CALL_DATA` пока остаются, потому что они относятся уже не к кэшу, а к старой модели вызовов.
