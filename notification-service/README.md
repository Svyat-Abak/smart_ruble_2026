# Журнал уведомлений

Отдельный Spring Boot сервис, порт 8085, PostgreSQL notification_db. Читает payment-events из Kafka. Один eventId создаёт одну запись; повторная доставка безопасна. Невалидное сообщение после повторов попадает в payment-events.DLT.

`GET /api/v1/notifications` через gateway возвращает 100 последних событий финансовому директору или администратору. Это общий журнал одной компании-заказчика; push/email и персональные подписки пока не реализованы.

Внутренние X-User-Id / X-User-Roles поступают от gateway. Сервис нельзя выставлять в интернет напрямую. Настройки DB_URL, DB_USERNAME, DB_PASSWORD, KAFKA_BOOTSTRAP_SERVERS задаются Compose. Схема создаётся schema.sql; эволюция схемы требует версионированных миграций.

Тесты: `./mvnw test`, нужен Docker. DeliveryTest поднимает настоящие PostgreSQL и Kafka, проверяет дубликат и доставку повреждённого события в DLT. Consumer group smartruble-notifications, auto commit выключен, подтверждение после обработки записи.
