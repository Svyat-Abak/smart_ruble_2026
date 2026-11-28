package com.smartruble.billing.payments;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class BankClient {
  private final RestClient client;

  record Command(UUID paymentId, BigDecimal amount, String account, String bik) {}

  record Result(UUID paymentId, String status, UUID reference) {}

  BankClient(
      @Value("${app.bank.url:http://localhost:8083}") String url,
      @Value("${app.bank.key:local-demo-bank-key}") String key) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(2000);
    client =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeader("X-Service-Key", key)
            .build();
  }

  Result transfer(UUID id, BigDecimal amount, String account, String bik) {
    return client
        .post()
        .uri("/internal/transfers")
        .body(new Command(id, amount, account, bik))
        .retrieve()
        .body(Result.class);
  }
}
