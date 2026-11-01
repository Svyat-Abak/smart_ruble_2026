/**
 * Модуль «Заявки на оплату и проведение» (этапы 3 и 5).
 * <ul>
 *     <li>заявка на оплату из статьи сметы контрагенту;</li>
 *     <li>машина состояний платежа, проверка правилами, проведение через abs-service.</li>
 * </ul>
 * Публикует события PaymentRequested, PaymentBlocked, PaymentApprovalRequired, PaymentCompleted, PaymentFailed.
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.3.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Платежи")
package com.smartruble.billing.payments;
