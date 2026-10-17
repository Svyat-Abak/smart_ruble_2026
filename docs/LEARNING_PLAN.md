# План обучения SmartRuble

Цель: научиться проектировать сложные сервисы, работать с Kafka и шлюзами.
Принцип: **одна новая концепция на этап**. Код пишет разработчик, Claude объясняет и делает ревью.

## Как проходит этап
1. Объяснение проблемы и вариантов решения (без кода).
2. Решение и ADR в `docs/adr/`.
3. Код. Режим зависит от задачи:
   - **ментор:** объясняю, вы пишете, я ревьюю (бизнес-логика, всё новое);
   - **каркас:** делаю заготовку с `TODO`, вы заполняете (инфраструктура);
   - **разбор:** пишу целиком и объясняю построчно (скучная обвязка).
4. Ревью по diff.
5. Систему ломаем специально и чиним.
6. Коммит и короткое резюме «чему научился».

## Этапы

- [x] **Подготовка:** auth-service, api-gateway, монорепозиторий (ADR 0001)
- [ ] **Этап 0. Инфраструктура billing-service**
  Каркас сервиса, своя база, модули Spring Modulith, Testcontainers, docker-compose.
  ❓ Почему у billing своя база, а не таблицы в базе auth?

  Сделано (Claude): каркас `billing-service/`, `billing_db` в docker-compose (порт 5434), `ModularityTests`,
  Testcontainers, запуск всей системы в Docker.

  Задания:
  - [ ] Прочитать каркас: `pom.xml` (зачем каждая зависимость), `package-info.java` модулей,
        `ModularityTests`, `TestcontainersConfiguration`, `TestBillingServiceApplication`.
  - [ ] Запустить `./mvnw test` в `billing-service/` и открыть диаграммы в `target/spring-modulith-docs`.
  - [ ] Сломать границы: создать публичный класс `accounts/internal/AccountSecret.java`, использовать его
        из `catalog`, запустить `ModularityTests`, прочитать ошибку, удалить оба класса.
  - [ ] Подключить pgAdmin к `localhost:5434` / `billing_db` / `billing`. Разобраться, зачем Liquibase
        таблицы `databasechangelog` и `databasechangeloglock`.
  - [ ] Ответить на вопрос этапа в `docs/adr/0002-database-per-service.md` (по образцу ADR 0001).
  - [ ] Закоммитить: `billing: stage 0 skeleton`, `infra: add billing to docker-compose`.
- [ ] **Этап 1. Модуль «Лицевые счета» (accounts)**
  Слои внутри модуля, DTO и сущности, валидация, пользователь из шлюза (`X-User-Id`), проверка владельца,
  интеграционные тесты.
  ❓ Что будет, если жилец подставит в URL чужой accountId?
- [ ] **Этап 2. Модуль «Услуги и тарифы» (catalog)**
  Версионирование данных во времени (`valid_from`/`valid_to`), деньги в `BigDecimal`.
  ❓ Почему нельзя просто сделать UPDATE цены в тарифе?
- [ ] **Этап 3. Модуль «Начисления и квитанции» (invoicing) + границы модулей**
  Доменная логика и расчёты (тесты сначала), неизменяемость выставленной квитанции, публичный API модуля,
  `ApplicationModules.verify()`.
  ❓ Чем модуль отличается от пакета?
- [ ] **Этап 4. События и Kafka** ← главный
  Внутренние события Spring → Transactional Outbox → Kafka (топики, партиции, ключи, consumer groups, offsets,
  at-least-once, идемпотентный consumer, DLT) → минимальный notification-service.
  ❓ Что увидит пользователь, если notification получит событие дважды?
- [ ] **Этап 5. abs-service (mock) + модуль «Платежи» (payments)**
  Машина состояний платежа, ключ идемпотентности, таймауты, сага и компенсации, сверка.
  ❓ abs списал деньги, а billing упал до записи результата. Как система это исправит?
- [ ] **Этап 6. integration-service**
  Адаптер / anti-corruption layer, SOAP-клиент, заглушки внешних систем, реестр начислений для ГИС ЖКХ.
- [ ] **Этап 7. Наблюдаемость**
  Распределённая трассировка (Micrometer Tracing + Zipkin/Jaeger), путь запроса gateway → billing → Kafka → notification.

## Журнал
| Этап | Дата | Чему научился |
|---|---|---|
| Подготовка | 2026-10-16 | auth-service (JWT, роли), api-gateway (Spring Cloud Gateway), монорепозиторий |
