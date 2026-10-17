package com.smartruble.billing;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Архитектурный тест модульного монолита.
 * Падает, если один модуль обращается к внутренним классам другого или модули зависят друг от друга по кругу.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(BillingServiceApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.forEach(System.out::println);
        modules.verify();
    }

    /** Генерирует диаграммы модулей (PlantUML) в target/spring-modulith-docs. */
    @Test
    void writesDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
