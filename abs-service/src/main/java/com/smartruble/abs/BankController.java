package com.smartruble.abs;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal")
class BankController {
  private final BankLedger ledger;
  private final JdbcTemplate db;
  private final String key;
  private final AtomicReference<String> mode = new AtomicReference<>("NORMAL");

  BankController(
      BankLedger ledger, JdbcTemplate db, @Value("${BANK_KEY:local-demo-bank-key}") String key) {
    this.ledger = ledger;
    this.db = db;
    this.key = key;
  }

  private void check(String provided) {
    if (!key.equals(provided)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  }

  @PostMapping("/transfers")
  Object transfer(
      @RequestHeader("X-Service-Key") String key, @Valid @RequestBody BankLedger.Command cmd)
      throws InterruptedException {
    check(key);
    String scenario = mode.getAndSet("NORMAL");
    var result = ledger.transfer(cmd, scenario.equals("REJECT_ONCE"));
    // The transaction is already committed. Models losing only the first HTTP response.
    if (scenario.equals("TIMEOUT_AFTER_COMMIT")) Thread.sleep(5000);
    return result;
  }

  record Mode(String mode) {}

  @PostMapping("/demo/mode")
  Object mode(@RequestHeader("X-Service-Key") String key, @RequestBody Mode req) {
    check(key);
    if (!Set.of("NORMAL", "REJECT_ONCE", "TIMEOUT_AFTER_COMMIT").contains(req.mode()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    mode.set(req.mode());
    return Map.of("mode", req.mode());
  }

  @GetMapping("/ledger")
  Object ledger(@RequestHeader("X-Service-Key") String key) {
    check(key);
    return Map.of(
        "balance",
        db.queryForObject(
            "select balance from bank_balance where id=1", java.math.BigDecimal.class),
        "transfers",
        db.queryForList("select * from bank_transfers order by created_at desc"));
  }
}
