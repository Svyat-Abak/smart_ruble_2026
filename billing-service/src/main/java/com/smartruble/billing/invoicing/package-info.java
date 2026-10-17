/**
 * Модуль «Начисления и квитанции» (этап 3).
 * <ul>
 *     <li>генерация квитанций и расчёт стоимости каждой позиции;</li>
 *     <li>выдача данных для приложения (REST API для Android);</li>
 *     <li>формирование реестра начислений для ГИС ЖКХ (отправляет integration-service).</li>
 * </ul>
 * Таблицы: invoices, invoice_items. Зависит от accounts и catalog — только через их публичный API.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Начисления и квитанции")
package com.smartruble.billing.invoicing;
