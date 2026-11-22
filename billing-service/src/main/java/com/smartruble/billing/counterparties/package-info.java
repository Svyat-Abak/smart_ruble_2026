/**
 * Модуль «Контрагенты» (этап 1).
 * <ul>
 *     <li>подрядчики и поставщики: реквизиты, тип (юрлицо, ИП, самозанятый);</li>
 *     <li>статус проверки: NEW, VERIFIED, SUSPICIOUS, BLOCKED.</li>
 * </ul>
 * Публичный API: найти контрагента, узнать, можно ли ему платить.
 * См. docs/BILLING_DECOMPOSITION.md, раздел 4.1.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Контрагенты")
package com.smartruble.billing.counterparties;
