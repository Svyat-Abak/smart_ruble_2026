/**
 * Модуль «Отчёты» (этап 7).
 * Строит свои таблицы для чтения из событий других модулей (read model, CQRS).
 * В чужие таблицы не ходит.
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.6.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Отчёты")
package com.smartruble.billing.reporting;
