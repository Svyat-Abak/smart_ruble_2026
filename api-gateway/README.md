# api-gateway

Единая точка входа SmartRuble: Spring Cloud Gateway (WebFlux), проверка JWT, роли, лимит запросов, circuit breaker,
CORS, общий Swagger. Своей базы данных нет.

```bash
./mvnw spring-boot:run      # порт 8080, auth-service ожидается на 8081
./mvnw test
```

Swagger всех сервисов: http://localhost:8080/swagger-ui.html

Подробное объяснение устройства — в [GUIDE.md](GUIDE.md).
