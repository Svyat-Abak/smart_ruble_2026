package com.smartruble.billing.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartruble.billing.access.Caller;
import com.smartruble.billing.counterparties.CounterpartyDirectory;
import com.smartruble.billing.counterparties.VerificationStatus;
import com.smartruble.billing.rules.PaymentRules;
import com.smartruble.billing.workorders.BudgetLedger;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class Payments {
  private final JdbcTemplate db;
  private final BudgetLedger budgets;
  private final CounterpartyDirectory directory;
  private final PaymentRules rules;
  private final ObjectMapper json;
  private final BankClient bank;

  public record Create(
      @NotNull UUID workOrderId,
      @NotNull UUID budgetLineId,
      @NotNull UUID recipientId,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
      @NotBlank @Size(max = 500) String purpose) {}

  public record Approval(boolean approve, @NotBlank @Size(max = 1000) String comment) {}

  public Map<String, Object> create(UUID key, Create req, Caller caller) {
    caller.require("CONTRACTOR", "MANAGER", "ADMIN");
    // Serializes only retries of the same command. The unique constraint remains the final guard.
    db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", caller.id() + ":" + key);
    var previous =
        db.queryForList(
            "select * from payment_requests where created_by=? and idempotency_key=?",
            caller.id(),
            key);
    if (!previous.isEmpty()) {
      var p = previous.getFirst();
      if (!req.workOrderId().equals(p.get("work_order_id"))
          || !req.budgetLineId().equals(p.get("budget_line_id"))
          || !req.recipientId().equals(p.get("recipient_id"))
          || req.amount().compareTo((BigDecimal) p.get("amount")) != 0
          || !req.purpose().equals(p.get("purpose")))
        throw error(409, "Ключ идемпотентности уже связан с другой заявкой");
      budgets.checkAccess(req.workOrderId(), caller);
      return p;
    }
    var line = budgets.lockLine(req.workOrderId(), req.budgetLineId(), caller);
    var recipient =
        directory
            .recipient(req.recipientId())
            .orElseThrow(() -> error(404, "Получатель не найден"));
    var decision =
        rules.evaluate(
            recipient.status(), recipient.account() != null, req.amount(), line.available());
    String status =
        switch (decision.outcome()) {
          case "ALLOW" -> "READY";
          case "REQUIRE_APPROVAL" -> "PENDING_APPROVAL";
          default -> "BLOCKED";
        };
    if (!status.equals("BLOCKED")) budgets.reserve(req.budgetLineId(), req.amount());
    UUID id = UUID.randomUUID();
    db.update(
        """
insert into payment_requests(id,idempotency_key,created_by,work_order_id,budget_line_id,recipient_id,
recipient_name,recipient_inn,recipient_account,recipient_bik,amount,purpose,status,decision,reason)
values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""",
        id,
        key,
        caller.id(),
        req.workOrderId(),
        req.budgetLineId(),
        recipient.id(),
        recipient.name(),
        recipient.inn(),
        recipient.account(),
        recipient.bik(),
        req.amount(),
        req.purpose(),
        status,
        decision.outcome(),
        decision.reason());
    if (status.equals("PENDING_APPROVAL"))
      db.update("insert into approval_tasks(payment_id,status) values(?,'PENDING')", id);
    event(id, status, decision.reason(), caller.id());
    return details(id, caller);
  }

  public List<Map<String, Object>> list(Caller caller) {
    var visible = budgets.visibleOrderIds(caller);
    return db.queryForList("select * from payment_requests order by created_at desc").stream()
        .filter(p -> visible.contains(p.get("work_order_id")))
        .toList();
  }

  public Map<String, Object> details(UUID id, Caller caller) {
    var rows = db.queryForList("select * from payment_requests where id=?", id);
    if (rows.isEmpty()) throw error(404, "Платёж не найден");
    var result = new LinkedHashMap<>(rows.getFirst());
    budgets.checkAccess((UUID) result.get("work_order_id"), caller);
    result.put(
        "history",
        db.queryForList(
            "select * from payment_status_history where payment_id=? order by changed_at,id", id));
    result.put("approval", db.queryForList("select * from approval_tasks where payment_id=?", id));
    return result;
  }

  public Map<String, Object> approve(UUID id, Approval req, Caller caller) {
    caller.require("FINANCE_DIRECTOR", "ADMIN");
    var rows = db.queryForList("select * from payment_requests where id=? for update", id);
    if (rows.isEmpty()) throw error(404, "Платёж не найден");
    var p = rows.getFirst();
    if (!p.get("status").equals("PENDING_APPROVAL")) throw error(409, "Заявка уже обработана");
    if (req.approve()
        && directory
            .find((UUID) p.get("recipient_id"))
            .map(c -> c.status() == VerificationStatus.BLOCKED)
            .orElse(true)) throw error(409, "Получатель заблокирован или отсутствует");
    String status = req.approve() ? "READY" : "REJECTED";
    db.update(
        "update approval_tasks set status=?,decided_by=?,decided_at=now(),comment=? where"
            + " payment_id=?",
        req.approve() ? "APPROVED" : "REJECTED",
        caller.id(),
        req.comment(),
        id);
    if (!req.approve())
      budgets.settle((UUID) p.get("budget_line_id"), (BigDecimal) p.get("amount"), false);
    event(id, status, req.comment(), caller.id());
    return details(id, caller);
  }

  /** The durable bank idempotency key is the payment UUID. A timeout keeps the reservation. */
  public UUID prepareNext() {
    var rows =
        db.queryForList(
            """
            select * from payment_requests where status in ('READY','PROCESSING')
            and (attempts=0 or updated_at < now()-interval '3 seconds')
            order by updated_at for update skip locked limit 1
            """);
    if (rows.isEmpty()) return null;
    var p = rows.getFirst();
    UUID id = (UUID) p.get("id");
    boolean alreadySent = p.get("status").equals("PROCESSING");
    // Once dispatch began, always reconcile with the same bank command, even if requisites change.
    if (!alreadySent) {
      var recipient = directory.recipient((UUID) p.get("recipient_id"));
      if (recipient.isEmpty()
          || recipient.get().status() == VerificationStatus.BLOCKED
          || !Objects.equals(recipient.get().account(), p.get("recipient_account"))
          || !Objects.equals(recipient.get().bik(), p.get("recipient_bik"))) {
        budgets.settle((UUID) p.get("budget_line_id"), (BigDecimal) p.get("amount"), false);
        event(id, "FAILED", "Реквизиты или статус получателя изменились до отправки", null);
        return null;
      }
      event(id, "PROCESSING", "Команда передана банковскому mock", null);
    }
    db.update("update payment_requests set attempts=attempts+1,updated_at=now() where id=?", id);
    return id;
  }

  /** Dispatch intent is committed by prepareNext before any HTTP side effect. */
  public void deliver(UUID id) {
    var p = db.queryForMap("select * from payment_requests where id=? for update", id);
    if (!p.get("status").equals("PROCESSING")) return;
    BankClient.Result result;
    try {
      result =
          bank.transfer(
              id,
              (BigDecimal) p.get("amount"),
              (String) p.get("recipient_account"),
              (String) p.get("recipient_bik"));
      if (result == null
          || !id.equals(result.paymentId())
          || !("SUCCEEDED".equals(result.status()) || "FAILED".equals(result.status())))
        throw new IllegalStateException("Unexpected bank response");
    } catch (org.springframework.web.client.RestClientException | IllegalStateException e) {
      db.update(
          "update payment_requests set last_error=? where id=?",
          "Ответ банка не подтверждён. Повторяем с тем же ключом.",
          id);
      return;
    }
    // Local accounting failures must roll back the whole transaction, not be treated as a network
    // timeout.
    budgets.settle(
        (UUID) p.get("budget_line_id"),
        (BigDecimal) p.get("amount"),
        result.status().equals("SUCCEEDED"));
    db.update(
        "update payment_requests set bank_reference=?,last_error=null where id=?",
        result.reference(),
        id);
    event(
        id,
        result.status(),
        result.status().equals("SUCCEEDED") ? "Банк подтвердил перевод" : "Банк отклонил перевод",
        null);
  }

  private void event(UUID id, String status, String reason, UUID actor) {
    db.update(
        "update payment_requests set status=?,reason=?,updated_at=now() where id=?",
        status,
        reason,
        id);
    db.update(
        "insert into payment_status_history(id,payment_id,status,reason,changed_by)"
            + " values(?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        status,
        reason,
        actor);
    UUID eventId = UUID.randomUUID();
    try {
      String payload =
          json.writeValueAsString(
              Map.of("eventId", eventId, "paymentId", id, "status", status, "message", reason));
      db.update(
          "insert into payment_outbox(id,payment_id,event_type,payload) values(?,?,?,?)",
          eventId,
          id,
          status,
          payload);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ResponseStatusException error(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }
}
