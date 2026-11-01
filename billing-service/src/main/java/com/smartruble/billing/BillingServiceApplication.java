package com.smartruble.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Модульный монолит. Каждый прямой подпакет com.smartruble.billing — отдельный модуль
 * (counterparties, workorders, payments, rules, approvals, reporting). Границы модулей проверяет ModularityTests.
 * Декомпозиция: docs/BILLING_DECOMPOSITION.md
 */
@SpringBootApplication
public class BillingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingServiceApplication.class, args);
	}

}
