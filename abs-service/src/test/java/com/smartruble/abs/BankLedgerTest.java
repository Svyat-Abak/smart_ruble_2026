package com.smartruble.abs;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BankLedgerTest {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
  }

  @Autowired BankLedger ledger;
  @Autowired JdbcTemplate db;
  @LocalServerPort int port;

  @BeforeEach
  void reset() {
    db.update("delete from bank_transfers");
    db.update("update bank_balance set balance=100000");
  }

  @Test
  void concurrentIdenticalTransfersDebitOnce() throws Exception {
    UUID id = UUID.randomUUID();
    var cmd =
        new BankLedger.Command(id, new BigDecimal("1000"), "40702810900000000001", "044525225");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> ledger.transfer(cmd, false));
      var b = pool.submit(() -> ledger.transfer(cmd, false));
      assertThat(a.get().reference()).isEqualTo(b.get().reference());
    }
    assertThat(db.queryForObject("select balance from bank_balance where id=1", BigDecimal.class))
        .isEqualByComparingTo("99000");
    assertThatThrownBy(
            () ->
                ledger.transfer(
                    new BankLedger.Command(id, new BigDecimal("2000"), cmd.account(), cmd.bik()),
                    false))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void timeoutAfterCommitRecoversThroughHttp() throws Exception {
    var client = HttpClient.newHttpClient();
    String url = "http://localhost:" + port + "/internal";
    var mode =
        HttpRequest.newBuilder(URI.create(url + "/demo/mode"))
            .header("X-Service-Key", "local-demo-bank-key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"mode\":\"TIMEOUT_AFTER_COMMIT\"}"))
            .build();
    assertThat(client.send(mode, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    String body =
        "{\"paymentId\":\""
            + UUID.randomUUID()
            + "\",\"amount\":1000,\"account\":\"40702810900000000001\",\"bik\":\"044525225\"}";
    var command =
        HttpRequest.newBuilder(URI.create(url + "/transfers"))
            .timeout(Duration.ofSeconds(2))
            .header("X-Service-Key", "local-demo-bank-key")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    assertThatThrownBy(() -> client.send(command, HttpResponse.BodyHandlers.ofString()))
        .isInstanceOf(HttpTimeoutException.class);
    assertThat(client.send(command, HttpResponse.BodyHandlers.ofString()).body())
        .contains("SUCCEEDED");
    assertThat(db.queryForObject("select balance from bank_balance where id=1", BigDecimal.class))
        .isEqualByComparingTo("99000");
    assertThat(db.queryForObject("select count(*) from bank_transfers", Integer.class)).isOne();
  }
}
