package com.smartruble.billing;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Приложение поднимается на настоящем PostgreSQL, миграции Liquibase применяются. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BillingServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
