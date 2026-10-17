package com.smartruble.billing;

import org.springframework.boot.SpringApplication;

/**
 * Запуск сервиса локально БЕЗ установленной базы: PostgreSQL поднимется в Docker автоматически.
 * Запуск: ./mvnw spring-boot:test-run  (или Run этого класса в IntelliJ).
 */
public class TestBillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(BillingServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
