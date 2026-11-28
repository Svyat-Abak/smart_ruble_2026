package com.smartruble.billing.counterparties;

public enum VerificationStatus {
    NEW,         // заведён, ещё не проверен
    VERIFIED,    // проверен, платить можно
    SUSPICIOUS,  // есть признаки риска — платежи только через согласование
    BLOCKED      // платить нельзя
}
