package com.smartruble.billing.rules;

import com.smartruble.billing.counterparties.VerificationStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class PaymentRules {
  public record Decision(String outcome, String reason) {}

  public Decision evaluate(
      VerificationStatus status,
      boolean bankAccountPresent,
      BigDecimal amount,
      BigDecimal available) {
    if (status == VerificationStatus.BLOCKED)
      return new Decision("BLOCK", "Получатель заблокирован");
    if (!bankAccountPresent)
      return new Decision("BLOCK", "У получателя нет действующего основного счёта");
    if (amount.compareTo(available) > 0)
      return new Decision("BLOCK", "Сумма превышает свободный остаток статьи");
    if (status != VerificationStatus.VERIFIED)
      return new Decision("REQUIRE_APPROVAL", "Получатель требует ручной проверки");
    if (amount.compareTo(new BigDecimal("50000.00")) >= 0)
      return new Decision("REQUIRE_APPROVAL", "Сумма от 50 000 рублей требует согласования");
    return new Decision("ALLOW", "Получатель и бюджет прошли проверку");
  }
}
