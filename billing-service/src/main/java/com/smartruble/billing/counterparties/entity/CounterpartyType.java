package com.smartruble.billing.counterparties.entity;

public enum CounterpartyType {
    LEGAL_ENTITY,             // юрлицо: ИНН 10 цифр, есть КПП
    INDIVIDUAL_ENTREPRENEUR,  // ИП: ИНН 12 цифр, ОГРНИП 15 цифр
    SELF_EMPLOYED             // самозанятый: ИНН 12 цифр
}
