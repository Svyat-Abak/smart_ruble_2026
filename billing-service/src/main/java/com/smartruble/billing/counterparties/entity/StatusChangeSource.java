package com.smartruble.billing.counterparties.entity;

public enum StatusChangeSource {
    MANUAL,     // сотрудник вручную
    FNS_CHECK   // автоматическая проверка по данным ФНС (этап 6)
}
