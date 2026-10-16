# SmartRuble

Учебная микросервисная система оплаты ЖКХ.

| Сервис | Порт | Статус | Назначение |
|---|---|---|---|
| [api-gateway](api-gateway) | 8080 | ✅ | единая точка входа: маршрутизация, проверка JWT, лимиты, CORS |
| [auth-service](auth-service) | 8081 | ✅ | регистрация, вход, роли, выдача JWT |
| billing-service | 8082 | ⏳ | лицевые счета, тарифы, начисления, платежи |
| abs-service | 8083 | ⏳ | mock банковской системы |
| integration-service | 8084 | ⏳ | ГИС ЖКХ, цифровой рубль, смарт-контракты |
| notification-service | 8085 | ⏳ | push / email по событиям из Kafka |

Наружу открыт только api-gateway; остальные сервисы доступны внутри сети.

## Запуск

```bash
cp .env.example .env
docker compose up --build
```

- API: http://localhost:8080
- Swagger всех сервисов: http://localhost:8080/swagger-ui.html
- PostgreSQL auth-service (для pgAdmin): `localhost:5433`, база `auth_db`, пользователь/пароль `auth`/`auth`

## Структура

- `<service>/`: отдельный Spring Boot сервис со своим `pom.xml`, Dockerfile и README
- `docs/adr/`: архитектурные решения
- `docker-compose.yml`: запуск всей системы

## Соглашения

- Коммиты с префиксом сервиса: `billing: add utility accounts CRUD`, `infra: add kafka`.
- Сервисы не импортируют код друг друга, а общаются только через HTTP и Kafka.
