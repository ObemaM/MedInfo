# 2026-05-06: Figma-макет экранов Android-приложения

## Кратко

Создан Figma-файл с редактируемой картой основных экранов MedInfo, собранной по текущим Android XML layout-файлам.

## Figma

- URL: https://www.figma.com/design/9KD06m2hmkAIjJGIvOen1V
- Файл: `MedInfo Android screens v1.1.2`
- Страница: `MedInfo Android UI Map`

## Добавленные фреймы

- `01 LoginActivity`
- `02 MainActivity · Requires decision`
- `03 MainActivity · Active`
- `04 ConfigActivity`
- `05 IncomingCallActivity`
- `06 HospitalizationDetailsActivity`
- `07 ChatActivity`
- `08 Dialogs and overlays`

## Источники

- `app/src/main/res/layout/activity_login.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_config.xml`
- `app/src/main/res/layout/activity_incoming_call.xml`
- `app/src/main/res/layout/activity_hospitalization_details.xml`
- `app/src/main/res/layout/activity_chat.xml`
- `app/src/main/res/layout/item_hospitalization.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/AndroidManifest.xml`

## Ограничение

Figma MCP остановил дальнейшие вызовы из-за лимита Starter-плана до финальной доводки высот текстовых слоев и screenshot-валидации. Фреймы и слои созданы, но перед использованием как точной базы для redesign желательно пройти отдельный cleanup: поправить возможное подрезание текста, сгруппировать повторяемые компоненты и визуально сверить скриншотами.
