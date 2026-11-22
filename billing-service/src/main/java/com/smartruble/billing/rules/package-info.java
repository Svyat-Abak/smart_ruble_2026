/**
 * Модуль «Движок правил» (этап 3).
 * Детерминированное и объяснимое решение по платежу: ALLOW, BLOCK, REQUIRE_APPROVAL.
 * Своей БД нет: чистая функция от заявки и контекста.
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.4.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Правила")
package com.smartruble.billing.rules;
