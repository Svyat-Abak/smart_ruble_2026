package com.smartruble.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Модульный монолит. Каждый прямой подпакет com.smartruble.billing — отдельный модуль
 * (accounts, catalog, invoicing, payments). Границы модулей проверяет ModularityTests.
 */
@SpringBootApplication
public class BillingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingServiceApplication.class, args);
	}

}
