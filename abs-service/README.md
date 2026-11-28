# Банковский mock

Отдельный Spring Boot сервис, порт 8083, PostgreSQL bank_db. В Compose не публикуется наружу.

`POST /internal/transfers`: paymentId, amount, account, bik. Повтор того же paymentId возвращает сохранённый результат; другое содержимое даёт 409. Баланс и запись перевода фиксируются одной транзакцией. Начальный баланс 10 000 000 условных рублей. Это debit-модель одного счёта, не реальная банковская интеграция.

Внутренние операции требуют X-Service-Key, значение BANK_KEY. `POST /internal/demo/mode` переключает следующий вызов в NORMAL, REJECT_ONCE или TIMEOUT_AFTER_COMMIT. `GET /internal/ledger` возвращает баланс и журнал. Удобные команды находятся в ../docs/MVP_GUIDE.md.

Тесты: `./mvnw test`, нужен Docker. BankLedgerTest использует PostgreSQL Testcontainers и проверяет конкурентный повтор и потерю ответа после commit. Схема создаётся schema.sql при запуске; для будущих изменений схемы потребуется версия миграций.
