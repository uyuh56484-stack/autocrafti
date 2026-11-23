# Локальный отчёт проверки

Дата подготовки: 2026-11-24

## Выполнено

- Java исходники прогнаны через Java Compiler API в режиме синтаксического разбора (`JavacTask.parse`) — синтаксических ошибок не найдено.
- Все JSON-файлы успешно разобраны.
- `neoforge.mods.toml` успешно разобран после подстановки значений из `gradle.properties` (так же работает Gradle `processResources`).
- Все PNG-файлы открываются без ошибок.
- Проверены ссылки blockstate → block model → текстуры, item model → block model, а также расположение recipe и loot table ресурсов для Minecraft 1.21.1.
- В проекте есть GitHub Actions workflow, который устанавливает Java 21 и Gradle 9.2.1, затем выполняет `gradle clean build` и сохраняет JAR как Artifact.

## Что не удалось выполнить в контейнере подготовки

Полная Gradle-компиляция и запуск клиента Minecraft не выполнены: в контейнере нет установленного Gradle и нет доступа к Maven/Gradle репозиториям. Поэтому отчёт не утверждает, что JAR уже успешно собран или что игровой smoke-test пройден. Для этих двух проверок предназначен GitHub Actions workflow и список `SMOKE_TEST.md`.
