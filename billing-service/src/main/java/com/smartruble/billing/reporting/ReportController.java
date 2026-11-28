package com.smartruble.billing.reporting;

import com.smartruble.billing.access.Caller;
import com.smartruble.billing.payments.Payments;
import com.smartruble.billing.workorders.WorkOrders;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/reports")
@RequiredArgsConstructor
class ReportController {
  private final WorkOrders orders;
  private final Payments payments;

  @GetMapping("/overview")
  Object overview(@RequestHeader HttpHeaders h) {
    Caller caller = Caller.from(h);
    return Map.of(
        "workorders",
        orders.list(caller).stream().map(o -> orders.details((UUID) o.get("id"), caller)).toList(),
        "payments",
        payments.list(caller));
  }
}
