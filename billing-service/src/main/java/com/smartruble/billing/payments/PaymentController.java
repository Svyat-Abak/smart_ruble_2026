package com.smartruble.billing.payments;

import com.smartruble.billing.access.Caller;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/payments")
@RequiredArgsConstructor
class PaymentController {
  private final Payments service;

  @GetMapping
  Object list(@RequestHeader HttpHeaders h) {
    return service.list(Caller.from(h));
  }

  @PostMapping
  Object create(
      @RequestHeader("Idempotency-Key") UUID key,
      @Valid @RequestBody Payments.Create req,
      @RequestHeader HttpHeaders h) {
    return service.create(key, req, Caller.from(h));
  }

  @GetMapping("/{id}")
  Object detail(@PathVariable UUID id, @RequestHeader HttpHeaders h) {
    return service.details(id, Caller.from(h));
  }

  @PostMapping("/{id}/decision")
  Object decide(
      @PathVariable UUID id,
      @Valid @RequestBody Payments.Approval req,
      @RequestHeader HttpHeaders h) {
    return service.approve(id, req, Caller.from(h));
  }
}
