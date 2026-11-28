package com.smartruble.billing.workorders;

import com.smartruble.billing.access.Caller;
import java.math.BigDecimal;
import java.util.*;

public interface BudgetLedger {
  record Line(UUID id, UUID orderId, BigDecimal available, String category) {}

  void checkAccess(UUID orderId, Caller caller);

  List<UUID> visibleOrderIds(Caller caller);

  Line lockLine(UUID orderId, UUID lineId, Caller caller);

  void reserve(UUID lineId, BigDecimal amount);

  void settle(UUID lineId, BigDecimal amount, boolean spent);
}
