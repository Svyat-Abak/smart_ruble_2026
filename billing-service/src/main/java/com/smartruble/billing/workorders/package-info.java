/**
 * Модуль «Распоряжения и бюджеты» (этап 2).
 * <ul>
 *     <li>распоряжение подрядчику: объект работ, сроки, бюджет, жизненный цикл;</li>
 *     <li>статьи сметы с лимитами; учёт резерва, трат и остатка.</li>
 * </ul>
 * Зависит от counterparties (только через его публичный API).
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.2.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Распоряжения и бюджеты")
package com.smartruble.billing.workorders;
