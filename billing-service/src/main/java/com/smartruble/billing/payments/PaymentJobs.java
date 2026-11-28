package com.smartruble.billing.payments;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
class PaymentJobs {
  private final Payments service;

  @Scheduled(fixedDelayString = "${app.jobs.payment-delay:1000}")
  void dispatch() {
    try {
      var id = service.prepareNext();
      if (id != null) service.deliver(id);
    } catch (Exception e) {
      LoggerFactory.getLogger(getClass()).error("Payment job rolled back; retrying next tick", e);
    }
  }
}
