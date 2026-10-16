# auth-service

Микросервис аутентификации/авторизации SmartRuble: Spring Boot 3.5, Spring Security, JWT, PostgreSQL, Liquibase, Swagger.

## Запуск

### Всё в Docker
```bash
docker compose up --build
```
PostgreSQL снаружи доступен на порту **5433**, чтобы не конфликтовать с локальным Postgres. Сервис работает на **8081**.

### Локально (Postgres уже установлен)
```bash
createuser -P auth            # пароль: auth
createdb -O auth auth_db
./mvnw spring-boot:run
```
Или только база из compose: `docker compose up auth-db` и `DB_URL=jdbc:postgresql://localhost:5433/auth_db ./mvnw spring-boot:run`.

Swagger UI: http://localhost:8081/swagger-ui.html (кнопка **Authorize**, вставить `accessToken`).

## Переменные окружения

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `8081` | порт |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/auth_db` / `auth` / `auth` | БД |
| `JWT_SECRET` | dev-ключ | Base64-ключ HMAC (≥ 256 бит). **В проде обязательно свой**: `openssl rand -base64 32` |
| `JWT_ISSUER` | `smartruble-auth-service` | claim `iss` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | `15m` / `30d` | время жизни токенов |
| `ADMIN_PHONE` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `+70000000000` / `admin@smartruble.local` / `admin12345` | администратор, создаётся при первом старте |
| `ADMIN_INIT_ENABLED` | `true` | создавать ли админа |

## Роли
`ROLE_EMPLOYEE`, `ROLE_MANAGER`, `ROLE_FINANCE_DIRECTOR`, `ROLE_ADMIN`.
При регистрации выдаётся `ROLE_EMPLOYEE`, остальные роли назначает администратор (`PUT /api/v1/admin/users/{id}/roles`).
Роли лежат в таблице `roles` (миграция `db/changelog/1.0/002-insert-roles.xml`) и в enum `RoleName`.
Новую роль добавляйте в оба места новым changeSet'ом. В других сервисах проверяйте роли так: `hasRole('MANAGER')`
или `hasAuthority('ROLE_MANAGER')`.

## Схема БД
- `users`: `id` uuid, `phone_number`, `email`, `password_hash`, `is_active`, `created_at`
- `roles`: `id`, `name`
- `user_role`: `user_id`, `role_id`
- `refresh_tokens`: `id`, `user_id`, `token`, `expires_at`

Время хранится в UTC.

## API

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| POST | `/api/v1/auth/register` | все | регистрация → пара токенов |
| POST | `/api/v1/auth/login` | все | вход по телефону и паролю |
| POST | `/api/v1/auth/refresh` | все | новая пара токенов (старый refresh отзывается) |
| POST | `/api/v1/auth/logout` | все | отзыв refresh-токена |
| GET | `/api/v1/auth/validate` | все | проверка `Authorization: Bearer …` (200 / 401 + `{valid, userId, roles}`) |
| GET | `/api/v1/users/me` | авторизованные | текущий пользователь |
| PUT | `/api/v1/users/me/password` | авторизованные | смена пароля |
| GET | `/api/v1/admin/users` | ROLE_ADMIN | список пользователей (пагинация) |
| GET | `/api/v1/admin/users/{id}` | ROLE_ADMIN | пользователь |
| PUT | `/api/v1/admin/users/{id}/roles` | ROLE_ADMIN | назначить роли |
| PATCH | `/api/v1/admin/users/{id}/status` | ROLE_ADMIN | блокировка / разблокировка |

Пример:
```bash
curl -X POST localhost:8081/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+79001234567","email":"u@example.com","password":"password123"}'
```

## Подключение других микросервисов

Access-токен — JWT с алгоритмом HS256:
```json
{ "iss": "smartruble-auth-service", "sub": "<userId UUID>", "phone": "+7900…",
  "email": "…", "roles": ["ROLE_EMPLOYEE"], "iat": …, "exp": … }
```
Есть два варианта:
1. **Локальная проверка** (рекомендуется): сервис проверяет подпись тем же `JWT_SECRET` и `iss`, а роли берёт из claim `roles`.
   Обращаться к auth-service для этого не нужно.
2. **Через API-gateway**: gateway вызывает `GET /api/v1/auth/validate` и передаёт `userId` и роли дальше в заголовках.

Refresh-токены непрозрачные и хранятся в БД. Они одноразовые: при `/refresh` старый удаляется и выдаётся новый.
После смены пароля, смены ролей или блокировки все refresh-токены пользователя удаляются.

## Тесты
```bash
./mvnw test
```
Интеграционные тесты используют H2 в режиме PostgreSQL.
