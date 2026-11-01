# SmartRuble

Учебная микросервисная B2B-система контроля расходов компании: распоряжения подрядчикам, бюджеты, проверка и проведение платежей.

| Сервис | Порт | Статус | Назначение |
|---|---|---|---|
| [api-gateway](api-gateway) | 8080 | ✅ | единая точка входа: маршрутизация, проверка JWT, лимиты, CORS |
| [auth-service](auth-service) | 8081 | ✅ | регистрация, вход, роли, выдача JWT |
| [billing-service](billing-service) | 8082 | 🚧 | контроль расходов: контрагенты, распоряжения и бюджеты, платежи, правила ([декомпозиция](docs/BILLING_DECOMPOSITION.md)) |
| abs-service | 8083 | ⏳ | mock банковской системы: счета компании и проведение переводов |
| integration-service | 8084 | ⏳ | проверка контрагентов (ФНС), цифровой рубль, смарт-контракты |
| notification-service | 8085 | ⏳ | push / email по событиям из Kafka (блокировки, согласования, оплаты) |

Наружу открыт только api-gateway; остальные сервисы доступны внутри сети.

## Запуск

```bash
cp .env.example .env
docker compose up --build
```

- API: http://localhost:8080
- Swagger всех сервисов: http://localhost:8080/swagger-ui.html
- PostgreSQL для pgAdmin:
  - auth-service: `localhost:5433`, база `auth_db`, пользователь/пароль `auth`/`auth`
  - billing-service: `localhost:5434`, база `billing_db`, пользователь/пароль `billing`/`billing`

Остановить: `docker compose down` (данные баз сохранятся в volume; `down -v` удалит их).

## Структура

- `<service>/`: отдельный Spring Boot сервис со своим `pom.xml`, Dockerfile и README
- `docs/adr/`: архитектурные решения
- `docs/LEARNING_PLAN.md`: план обучения по этапам
- `docs/DOMAIN.md`: предметная область; `docs/BILLING_DECOMPOSITION.md`: модули billing
- `docker-compose.yml`: запуск всей системы

## Соглашения

- Коммиты с префиксом сервиса: `billing: add counterparties CRUD`, `infra: add kafka`.
- Сервисы не импортируют код друг друга, а общаются только через HTTP и Kafka.
