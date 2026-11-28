package com.smartruble.abs;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankLedger {
  private final JdbcTemplate db;

  public BankLedger(JdbcTemplate db) {
    this.db = db;
  }

  public record Command(
      @NotNull UUID paymentId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotNull @Pattern(regexp = "[0-9]{20}") String account,
      @NotNull @Pattern(regexp = "[0-9]{9}") String bik) {}

  public record Result(UUID paymentId, String status, UUID reference) {}

  @Transactional
  public Result transfer(Command cmd, boolean reject) {
    db.queryForObject("select balance from bank_balance where id=1 for update", BigDecimal.class);
    var previous =
        db.queryForList("select * from bank_transfers where payment_id=?", cmd.paymentId());
    if (!previous.isEmpty()) {
      var p = previous.getFirst();
      if (((BigDecimal) p.get("amount")).compareTo(cmd.amount()) != 0
          || !p.get("account").equals(cmd.account())
          || !p.get("bik").equals(cmd.bik()))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency payload mismatch");
      return new Result(cmd.paymentId(), (String) p.get("status"), (UUID) p.get("reference"));
    }
    int debit =
        reject
            ? 0
            : db.update(
                "update bank_balance set balance=balance-? where id=1 and balance>=?",
                cmd.amount(),
                cmd.amount());
    var result =
        new Result(cmd.paymentId(), debit == 1 ? "SUCCEEDED" : "FAILED", UUID.randomUUID());
    db.update(
        "insert into bank_transfers(payment_id,amount,account,bik,status,reference)"
            + " values(?,?,?,?,?,?)",
        cmd.paymentId(),
        cmd.amount(),
        cmd.account(),
        cmd.bik(),
        result.status(),
        result.reference());
    return result;
  }
}
