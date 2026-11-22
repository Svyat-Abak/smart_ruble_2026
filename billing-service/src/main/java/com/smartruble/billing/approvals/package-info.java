/**
 * Модуль «Ручное согласование» (этап 4).
 * Очередь платежей, требующих решения финансового директора.
 * С модулем payments общается только событиями.
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.5.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Согласование")
package com.smartruble.billing.approvals;
