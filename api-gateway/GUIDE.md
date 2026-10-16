# API Gateway: как он устроен и как сделать такой самому

Гайд по сервису `api-gateway` проекта SmartRuble. Идём от идеи к коду, каждый файл разобран отдельно.

---

## 1. Что такое API Gateway и зачем он нужен

Без шлюза мобильное приложение должно знать адреса всех сервисов: auth на `:8081`, billing на `:8082` и так далее.
Каждый сервис сам проверяет токены, сам настраивает CORS и сам защищается от перегрузки. Когда сервисов много,
это быстро превращается в кашу.

**Шлюз — это единственная дверь в систему:**

```
                       ┌──────────────────────────── внутренняя сеть ─────────────────────┐
 Android / Web  ──►  api-gateway :8080 ──► auth-service         :8081
                     (проверка JWT,   ──► billing-service      :8082 ──► abs-service         (наружу закрыт)
                      роли, лимиты,   ──► notification-service :8085      integration-service (наружу закрыт)
                      CORS, логи)
                       └──────────────────────────────────────────────────────────────────┘
```

Что делает шлюз:

| Задача | Как это сделано у нас |
|---|---|
| Маршрутизация: какой сервис обрабатывает URL | `routes` в `application.yaml` |
| Аутентификация: валиден ли токен | Spring Security + OAuth2 Resource Server (`SecurityConfig`) |
| Грубая авторизация: например, `/admin/**` только для ADMIN | `SecurityConfig.authorizeExchange` |
| Передача пользователя во внутренние сервисы | `UserHeadersFilter`, заголовки `X-User-*` |
| Защита от перегрузки | `RateLimitFilter` (лимит запросов) и `CircuitBreaker` с `FallbackController` |
| CORS для веб-фронта | `SecurityConfig.corsConfigurationSource` |
| Сквозной id запроса и логи | `RequestIdFilter` |
| Общий Swagger для всех сервисов | springdoc + маршруты `*-openapi` |

**Чего шлюз НЕ делает:** бизнес-логики, работы с базой (у шлюза её нет), выдачи токенов (это задача auth-service).

---

## 2. Да, это отдельный Spring Boot сервис, но реактивный

Шлюз — обычное Spring Boot приложение со своим `pom.xml`, портом и Dockerfile. Главное отличие от
auth-service в том, что он построен на **Spring Cloud Gateway**, а эта библиотека работает на **WebFlux + Netty**
(реактивный стек), а не на Spring MVC + Tomcat.

Почему так. Шлюз почти ничего не вычисляет, он в основном ждёт ответов от других сервисов. В Spring MVC
на каждый запрос нужен свой поток, и пока сервис думает, поток простаивает. В WebFlux несколько потоков обслуживают
тысячи одновременных соединений, потому что ожидание никого не блокирует. Для прокси это ровно то, что нужно.

Что из этого следует при написании кода:

| Spring MVC (auth-service) | WebFlux (gateway) |
|---|---|
| `HttpServletRequest` | `ServerWebExchange` (запрос + ответ + атрибуты) |
| `OncePerRequestFilter` | `GlobalFilter` / `WebFilter` |
| метод возвращает объект | метод возвращает `Mono<...>` (0 или 1 значение «в будущем») / `Flux<...>` |
| `SecurityContextHolder.getContext()` | `exchange.getPrincipal()` / `ReactiveSecurityContextHolder` |
| `@EnableWebSecurity`, `HttpSecurity` | `@EnableWebFluxSecurity`, `ServerHttpSecurity` |

> ⚠️ Поэтому в шлюз нельзя добавлять `spring-boot-starter-web`: он тянет Tomcat и MVC и ломает Gateway.

---

## 3. Как создать такой проект с нуля

1. https://start.spring.io → Maven, Java 21, Spring Boot 3.5.x.
2. Зависимости: **Reactive Gateway**, **OAuth2 Resource Server**, **Resilience4J (Reactive)**, **Spring Boot Actuator**,
   **Validation**.
3. Вручную добавить `springdoc-openapi-starter-webflux-ui`. Обратите внимание: `webflux`, а не `webmvc`.

Ключевые места [pom.xml](pom.xml):

```xml
<!-- версия Spring Cloud обязана соответствовать версии Boot: 2025.0.x ↔ Boot 3.5.x -->
<spring-cloud.version>2025.0.0</spring-cloud.version>

<artifactId>spring-cloud-starter-gateway-server-webflux</artifactId>  <!-- сам шлюз -->
<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>  <!-- проверка JWT -->
<artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>

<!-- BOM: задаёт версии всех spring-cloud-* библиотек -->
<dependencyManagement> ... spring-cloud-dependencies ... </dependencyManagement>
```

> В старых туториалах встречается `spring-cloud-starter-gateway` и свойства `spring.cloud.gateway.routes`.
> С Spring Cloud 2025.0 стартер называется `spring-cloud-starter-gateway-server-webflux`,
> а свойства переехали в `spring.cloud.gateway.server.webflux.*`.

---

## 4. Путь одного запроса через шлюз

Разберём `GET /api/v1/users/me` с заголовком `Authorization: Bearer eyJ...`:

```
1. Netty принимает HTTP-запрос
2. WebFilter'ы Spring Security (SecurityConfig):
     ├─ CORS (для preflight OPTIONS ответ формируется прямо здесь)
     ├─ BearerTokenAuthenticationFilter: достаёт токен → ReactiveJwtDecoder проверяет подпись, exp, iss
     │     ✗ невалиден → JsonAuthenticationEntryPoint → 401, дальше запрос не идёт
     └─ authorizeExchange: /users/** требует authenticated()
           ✗ не хватает роли → JsonAccessDeniedHandler → 403
3. RoutePredicateHandlerMapping: перебирает маршруты, Path=/api/v1/users/** → маршрут auth-service
     ✗ ни один маршрут не подошёл → 404
4. Цепочка GatewayFilter'ов (отсортирована по getOrder()):
     ├─ RequestIdFilter      — X-Request-Id, запоминает время начала
     ├─ RateLimitFilter      — забрать жетон из ведра пользователя, иначе 429
     ├─ UserHeadersFilter    — удалить присланные клиентом X-User-*, добавить настоящие из токена
     ├─ DedupeResponseHeader — default-filter из yaml
     ├─ CircuitBreaker       — фильтр маршрута; ошибка/таймаут → forward:/fallback/auth-service → 503
     └─ NettyRoutingFilter   — собственно HTTP-запрос в http://localhost:8081/api/v1/users/me
5. Ответ сервиса идёт обратно через те же фильтры; RequestIdFilter пишет строку в лог
```

Это главная картинка, которую стоит запомнить: **предикат выбирает маршрут, фильтры меняют запрос и ответ,
URI говорит, куда отправить**.

---

## 5. Маршруты: `application.yaml`

Файл: [src/main/resources/application.yaml](src/main/resources/application.yaml)

```yaml
spring.cloud.gateway.server.webflux:
  routes:
    - id: auth-service                                   # имя маршрута (в логах и /actuator/gateway/routes)
      uri: ${AUTH_SERVICE_URL:http://localhost:8081}     # куда проксировать
      predicates:                                        # КОГДА маршрут подходит
        - Path=/api/v1/auth/**, /api/v1/users/**, /api/v1/admin/**
      filters:                                           # ЧТО сделать с запросом
        - name: CircuitBreaker
          args:
            name: auth-service
            fallbackUri: forward:/fallback/auth-service
```

Три понятия:
- **Predicate** — условие. Кроме `Path`, бывают `Method=GET`, `Header=X-Version, 2`, `Host=**.smartruble.ru` и другие.
  Все предикаты маршрута должны выполниться одновременно.
- **URI** — адрес сервиса. `${AUTH_SERVICE_URL:http://localhost:8081}` означает «возьми из переменной окружения,
  а если её нет, используй значение по умолчанию». Локально сервис живёт на localhost, в Docker на `http://auth-service:8081`.
- **Filter** — модификация запроса или ответа. Встроенных фильтров много: `RewritePath`, `StripPrefix`,
  `AddRequestHeader`, `Retry`, `CircuitBreaker`, `RequestRateLimiter` и другие.

Путь по умолчанию передаётся **как есть**: `/api/v1/users/me` на шлюзе станет `/api/v1/users/me` в auth-service.
Если путь нужно изменить, используется `RewritePath`. Например, для Swagger:

```yaml
- id: auth-service-openapi
  uri: ${AUTH_SERVICE_URL:http://localhost:8081}
  predicates: [ Path=/auth-service/v3/api-docs ]
  filters:
    - RewritePath=/auth-service/(?<path>.*), /$\{path}    # /auth-service/v3/api-docs → /v3/api-docs
```

**Как добавить новый сервис**, например когда появится billing:
1. Добавить маршрут (у нас он уже есть, `/api/v1/billing/**` → `BILLING_SERVICE_URL`).
2. Если у сервиса есть Swagger, добавить маршрут `*-openapi` и строку в `springdoc.swagger-ui.urls`.
3. Если есть публичные эндпоинты, добавить их в `PUBLIC_PATHS` в `SecurityConfig`.

**abs-service и integration-service специально не опубликованы.** Клиенту нельзя напрямую «провести банковскую
транзакцию». Эти сервисы вызывает только billing внутри сети, а снаружи до них не дойти.

---

## 6. Безопасность: `SecurityConfig`

Файл: [security/SecurityConfig.java](src/main/java/com/smartruble/apigateway/security/SecurityConfig.java)

### 6.1 Почему шлюз проверяет JWT сам, а не спрашивает auth-service

Есть два подхода:

| | Локальная проверка (у нас) | Запрос в auth-service `/validate` |
|---|---|---|
| Как | Проверяем подпись тем же секретом | На каждый запрос HTTP-вызов в auth |
| Скорость | Микросекунды | +1 сетевой вызов на каждый запрос |
| Если auth-service упал | Всё работает, кроме логина | Не работает **ничего** |
| Блокировка пользователя | Действует, когда истечёт access-токен (15 мин) | Сразу |

JWT для того и придуман, чтобы его можно было проверить без похода в базу. Поэтому access-токен живёт недолго
(15 минут), а refresh-токен хранится в БД auth-service, и при блокировке пользователя он удаляется.

### 6.2 Декодер токена

```java
SecretKey key = new SecretKeySpec(Base64.decode(secret), "HmacSHA256");
NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer)); // + проверка exp и nbf
```

**Главное правило:** `app.jwt.secret` и `app.jwt.issuer` в шлюзе должны **совпадать** с `jwt.secret` и `jwt.issuer`
в auth-service. Иначе любой токен получит 401. В Docker это обеспечивает общий файл `../.env`.

### 6.3 Роли из токена

auth-service кладёт в токен `"roles": ["ROLE_EMPLOYEE", "ROLE_MANAGER"]`. По умолчанию Spring ищет роли в claim
`scope` и добавляет префикс `SCOPE_`, поэтому настраиваем:

```java
authorities.setAuthoritiesClaimName("roles"); // читать из claim "roles"
authorities.setAuthorityPrefix("");           // префикс ROLE_ уже есть в токене
```

После этого работает `hasRole("ADMIN")`: Spring сам допишет `ROLE_` и сравнит с `ROLE_ADMIN`.

### 6.4 Правила доступа

```java
.pathMatchers(HttpMethod.OPTIONS).permitAll()        // CORS preflight браузер шлёт без токена
.pathMatchers(PUBLIC_PATHS).permitAll()              // login, register, swagger...
.pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
.anyExchange().authenticated()                       // всё остальное — только с токеном
```

**Порядок важен:** срабатывает первое подходящее правило.

На шлюзе стоит делать только **грубые** проверки по префиксам URL. Тонкие правила вроде «финансовый директор видит
отчёты, а сотрудник только свои начисления» живут в самих сервисах. Там больше контекста, и это их зона ответственности.

### 6.5 JSON-ответы 401 и 403

`JsonAuthenticationEntryPoint` (401) и `JsonAccessDeniedHandler` (403) пишут ошибку через `ErrorResponseWriter`
в том же формате, что и auth-service. Так клиенту не важно, кто именно отказал, шлюз или сервис.

---

## 7. Глобальные фильтры

`GlobalFilter` применяется ко **всем** маршрутам. Шаблон:

```java
@Component
public class MyFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // "до": можно изменить запрос
        ServerHttpRequest request = exchange.getRequest().mutate().header("X-Foo", "bar").build();
        return chain.filter(exchange.mutate().request(request).build())   // передать дальше
                .doFinally(s -> { /* "после": ответ уже ушёл */ });
    }
    @Override
    public int getOrder() { return 0; }  // меньше = раньше
}
```

Объекты запроса **неизменяемые**. Поэтому используется паттерн `mutate()...build()`: создаём изменённую копию и
передаём дальше её. Порядок наших фильтров задан в `FilterOrder`.

### 7.1 `RequestIdFilter`: сквозной id
У каждого запроса появляется `X-Request-Id`. Если его прислал клиент, используется присланный, иначе генерируется UUID.
id уходит в сервис и возвращается клиенту. Если пользователь пожалуется «у меня ошибка», по этому id можно найти запрос
в логах всех сервисов. Фильтр также пишет строку вида
`GET /api/v1/users/me -> auth-service [200 OK] 25 ms (requestId=...)`.

### 7.2 `UserHeadersFilter`: кто пользователь
Достаёт данные из уже проверенного токена и добавляет заголовки `X-User-Id`, `X-User-Phone`, `X-User-Roles`.
Сервисы вроде billing могут просто прочитать `X-User-Id`, не разбирая JWT.

> 🔐 **Важный момент безопасности.** Фильтр сначала **удаляет** такие заголовки из входящего запроса.
> Иначе злоумышленник прислал бы `X-User-Roles: ROLE_ADMIN` и получил бы права админа во внутреннем сервисе.
> Такие заголовки можно доверять только потому, что внутренние сервисы закрыты от внешнего мира
> (в docker-compose у них нет `ports`).

### 7.3 `RateLimitFilter`: защита от перегрузки
Алгоритм **token bucket** («ведро с жетонами»):
- У каждого клиента ведро на `capacity` жетонов (по умолчанию 100).
- Каждый запрос забирает один жетон.
- Жетоны пополняются со скоростью `refill-per-second` (по умолчанию 20 в секунду).
- Ведро пустое — ответ `429 Too Many Requests` с заголовком `Retry-After`.

Так клиент может сделать короткий всплеск запросов, например при открытии экрана, но не может долго заваливать систему.
Клиент определяется по `userId` из токена, а если токена нет (логин, регистрация), по IP. Это заодно защищает от
перебора паролей.

Ограничение: счётчики хранятся в памяти одного экземпляра. Если запустить три шлюза, лимит фактически утроится.
Для такого случая в Spring Cloud Gateway есть фильтр `RequestRateLimiter` с хранилищем в Redis:
```yaml
filters:
  - name: RequestRateLimiter
    args:
      redis-rate-limiter.replenishRate: 20
      redis-rate-limiter.burstCapacity: 100
      key-resolver: "#{@userKeyResolver}"
```

### 7.4 Circuit breaker и `FallbackController`
Допустим, billing-service завис. Без защиты каждый запрос висел бы до таймаута, потоки и соединения шлюза копились бы,
и в итоге лёг бы весь шлюз. Это называется каскадный отказ.

**Circuit breaker** («автомат-предохранитель», Resilience4j) работает так:
- **CLOSED** — всё нормально, запросы идут в сервис.
- Если среди последних 20 вызовов 50% и больше закончились ошибкой, автомат переходит в **OPEN**. 15 секунд
  запросы в сервис вообще не отправляются, клиент сразу получает 503 от `FallbackController`.
- Затем **HALF_OPEN**: пропускаем 3 пробных запроса. Если они успешны, возвращаемся в CLOSED, иначе снова OPEN.

Настройки лежат в `resilience4j.*` в `application.yaml`. Учтите, что `timelimiter.timeout-duration` по умолчанию
равен 1 секунде, а этого мало для логина (bcrypt специально работает медленно), поэтому у нас стоит 10s.

Состояние автоматов видно в `/actuator/health`: сам статус доступен всем, а детали по каждому circuit breaker видны только с токеном ADMIN.

---

## 8. CORS

Браузер не даст фронту с `http://localhost:3000` обратиться к `http://localhost:8080`, если сервер явно этого не разрешит.
Перед «опасными» запросами браузер сначала шлёт `OPTIONS` (preflight). Этот запрос отправляется без токена,
поэтому `OPTIONS` у нас `permitAll()`.

Разрешённые адреса задаются в `app.cors.allowed-origins` (переменная `CORS_ALLOWED_ORIGINS`). CORS настраивается
**только на шлюзе**. Чтобы заголовки не задвоились, если их добавит ещё и сервис, есть default-filter
`DedupeResponseHeader`. Мобильному приложению CORS не нужен, это чисто браузерный механизм.

---

## 9. Общий Swagger

`http://localhost:8080/swagger-ui.html`: справа вверху выпадающий список сервисов.

Как это устроено:
1. springdoc в шлюзе поднимает только UI, а список документаций берёт из `springdoc.swagger-ui.urls`.
2. UI запрашивает `/auth-service/v3/api-docs`, маршрут `auth-service-openapi` переписывает путь и проксирует в auth-service.
3. В auth-service указан относительный адрес сервера `@Server(url = "/")`, поэтому «Try it out» отправляет запросы
   на тот же хост, то есть через шлюз.

Чтобы вызвать защищённый метод: выполните `login`, скопируйте `accessToken`, нажмите **Authorize** и вставьте токен.

---

## 10. Запуск и проверка

### Локально
```bash
# 1. auth-service (нужна база auth_db, см. auth-service/README.md)
cd ../auth-service && ./mvnw spring-boot:run
# 2. шлюз
cd ../api-gateway && ./mvnw spring-boot:run
```

### Всё в Docker (из папки `java/`)
```bash
docker compose up --build
```
Снаружи открыт только порт 8080 (шлюз) и 5433 (база для pgAdmin).

### Ручная проверка через curl
```bash
# вход
curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"+70000000000","password":"admin12345"}'

# скопируйте accessToken
TOKEN=eyJ...
curl -i localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN"   # 200 + X-Request-Id
curl -i localhost:8080/api/v1/users/me                                     # 401
curl -i localhost:8080/api/v1/billing/invoices -H "Authorization: Bearer $TOKEN"  # 503, billing ещё нет
curl -s localhost:8080/actuator/gateway/routes -H "Authorization: Bearer $TOKEN"  # список маршрутов (ADMIN)
```

### Автотесты
```bash
./mvnw test
```
В тестах (`GatewayRoutingTest`, `RateLimitTest`) вместо auth-service поднимается **MockWebServer**, поддельный
HTTP-сервер. Это позволяет проверить сам шлюз:
- что запрос дошёл до сервиса и с какими заголовками (`authService.takeRequest()`);
- что без токена или с поддельным токеном запрос **не** дошёл;
- что подделка `X-User-Roles` не работает;
- 403 на `/admin/**` без роли ADMIN, 503 при упавшем сервисе, 429 при превышении лимита, CORS preflight.

Токены в тестах выпускает `TestTokens` тем же секретом, что и auth-service.

---

## 11. Что нужно сделать в будущих сервисах (billing и др.)

1. Порт сервиса должен совпадать с маршрутом в шлюзе (billing — 8082, notification — 8085).
2. Пути контроллеров начинаются с префикса маршрута: `@RequestMapping("/api/v1/billing/...")`.
3. Узнать пользователя можно двумя способами:
   - **просто:** прочитать заголовки `@RequestHeader("X-User-Id") UUID userId` и `X-User-Roles`;
   - **надёжнее:** подключить `spring-boot-starter-oauth2-resource-server` и проверять JWT тем же секретом
     (настройка как в `SecurityConfig` шлюза). Тогда работают `@PreAuthorize("hasRole('FINANCE_DIRECTOR')")`.
4. Порт сервиса не публиковать наружу в docker-compose, вход только через шлюз.
5. Пробрасывать `X-Request-Id` в логи (MDC) и в вызовы других сервисов.

---

## 12. Структура проекта

```
api-gateway/
├── pom.xml
├── Dockerfile
├── GUIDE.md                         ← этот файл
└── src/main/
    ├── resources/application.yaml   ← маршруты, таймауты, circuit breaker, JWT, лимиты, CORS, swagger
    └── java/com/smartruble/apigateway/
        ├── ApiGatewayApplication.java
        ├── config/GatewayProperties.java        ← типизированные настройки app.*
        ├── security/
        │   ├── SecurityConfig.java              ← JWT, роли, правила доступа, CORS
        │   ├── JsonAuthenticationEntryPoint.java  (401)
        │   └── JsonAccessDeniedHandler.java       (403)
        ├── filter/
        │   ├── FilterOrder.java                 ← порядок фильтров
        │   ├── RequestIdFilter.java
        │   ├── RateLimitFilter.java
        │   └── UserHeadersFilter.java
        └── web/
            ├── FallbackController.java          (503)
            └── ErrorResponseWriter.java         ← единый JSON-формат ошибок
```

## 13. Переменные окружения

| Переменная | По умолчанию | Что это |
|---|---|---|
| `SERVER_PORT` | `8080` | порт шлюза |
| `AUTH_SERVICE_URL` | `http://localhost:8081` | адрес auth-service |
| `BILLING_SERVICE_URL` | `http://localhost:8082` | адрес billing-service |
| `NOTIFICATION_SERVICE_URL` | `http://localhost:8085` | адрес notification-service |
| `JWT_SECRET` / `JWT_ISSUER` | как в auth-service | **должны совпадать с auth-service** |
| `RATE_LIMIT_ENABLED` | `true` | включить лимит запросов |
| `RATE_LIMIT_CAPACITY` / `RATE_LIMIT_REFILL_PER_SECOND` | `100` / `20` | параметры token bucket |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | адреса веб-фронта |
