package com.smartruble.billing.workorders;

import com.smartruble.billing.access.Caller;
import com.smartruble.billing.counterparties.CounterpartyDirectory;
import com.smartruble.billing.counterparties.VerificationStatus;
import jakarta.validation.Valid;
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
public class WorkOrders implements BudgetLedger {
  private final JdbcTemplate db;
  private final CounterpartyDirectory directory;

  public record Budget(
      @NotBlank @Pattern(regexp = "MATERIALS|EQUIPMENT|LABOR|TRANSPORT|OTHER") String category,
      @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal limit) {}

  public record Create(
      @NotBlank @Size(max = 255) String title,
      @NotBlank @Size(max = 500) String workObject,
      @NotNull UUID contractorId,
      @NotEmpty @Size(max = 5) List<@Valid Budget> lines) {}

  public Map<String, Object> create(Create req, Caller caller) {
    caller.require("MANAGER", "ADMIN");
    var contractor =
        directory.find(req.contractorId()).orElseThrow(() -> error(404, "Подрядчик не найден"));
    if (contractor.status() != VerificationStatus.VERIFIED)
      throw error(409, "Выберите проверенного подрядчика");
    if (req.lines().stream().map(Budget::category).distinct().count() != req.lines().size())
      throw error(400, "Статьи сметы повторяются");
    UUID id = UUID.randomUUID();
    BigDecimal total =
        req.lines().stream().map(Budget::limit).reduce(BigDecimal.ZERO, BigDecimal::add);
    db.update(
        "insert into work_orders(id,title,work_object,contractor_id,manager_id,total_budget,status)"
            + " values(?,?,?,?,?,?,'DRAFT')",
        id,
        req.title(),
        req.workObject(),
        req.contractorId(),
        caller.id(),
        total);
    for (Budget line : req.lines())
      db.update(
          "insert into budget_lines(id,work_order_id,category,limit_amount) values(?,?,?,?)",
          UUID.randomUUID(),
          id,
          line.category(),
          line.limit());
    audit(id, "DRAFT", caller.id(), "Создано распоряжение");
    return details(id, caller);
  }

  public List<Map<String, Object>> list(Caller caller) {
    List<UUID> ids = visibleOrderIds(caller);
    return db.queryForList("select * from work_orders order by created_at desc").stream()
        .filter(row -> ids.contains(row.get("id")))
        .toList();
  }

  public List<UUID> visibleOrderIds(Caller caller) {
    if (caller.has("ADMIN", "FINANCE_DIRECTOR"))
      return db.queryForList("select id from work_orders", UUID.class);
    if (caller.has("MANAGER"))
      return db.queryForList(
          "select id from work_orders where manager_id=?", UUID.class, caller.id());
    if (caller.has("CONTRACTOR"))
      return directory
          .findCompanyOf(caller.id())
          .map(
              id ->
                  db.queryForList(
                      "select id from work_orders where contractor_id=?", UUID.class, id))
          .orElse(List.of());
    return db.queryForList(
        "select work_order_id from work_order_assignees where user_id=?", UUID.class, caller.id());
  }

  public void checkAccess(UUID id, Caller caller) {
    if (!visibleOrderIds(caller).contains(id)) throw error(404, "Распоряжение не найдено");
  }

  public Map<String, Object> details(UUID id, Caller caller) {
    checkAccess(id, caller);
    var result = new LinkedHashMap<>(db.queryForMap("select * from work_orders where id=?", id));
    result.put(
        "lines",
        db.queryForList(
            "select *,limit_amount-reserved_amount-spent_amount as available from budget_lines"
                + " where work_order_id=? order by category",
            id));
    result.put(
        "history",
        db.queryForList(
            "select * from work_order_status_history where work_order_id=? order by changed_at,id",
            id));
    return result;
  }

  public Map<String, Object> transition(UUID id, String target, Caller caller) {
    checkAccess(id, caller);
    String current =
        db.queryForObject("select status from work_orders where id=? for update", String.class, id);
    if (target.equals("ACTIVE")) {
      caller.require("FINANCE_DIRECTOR", "ADMIN");
      if (!current.equals("DRAFT")) throw error(409, "Утвердить можно только черновик");
    } else if (target.equals("CLOSED")) {
      caller.require("MANAGER", "FINANCE_DIRECTOR", "ADMIN");
      if (!current.equals("ACTIVE"))
        throw error(409, "Закрыть можно только действующее распоряжение");
      BigDecimal reserved =
          db.queryForObject(
              "select coalesce(sum(reserved_amount),0) from budget_lines where work_order_id=?",
              BigDecimal.class,
              id);
      if (reserved.signum() > 0)
        throw error(409, "Сначала завершите или отклоните зарезервированные платежи");
    } else throw error(400, "Неизвестный статус");
    db.update("update work_orders set status=? where id=?", target, id);
    audit(id, target, caller.id(), "Изменён статус распоряжения");
    return details(id, caller);
  }

  public void assign(UUID id, UUID userId, Caller caller) {
    checkAccess(id, caller);
    caller.require("MANAGER", "ADMIN");
    db.update("insert into work_order_assignees values(?,?) on conflict do nothing", id, userId);
  }

  public Line lockLine(UUID orderId, UUID lineId, Caller caller) {
    checkAccess(orderId, caller);
    String status =
        db.queryForObject(
            "select status from work_orders where id=? for update", String.class, orderId);
    if (!status.equals("ACTIVE")) throw error(409, "Распоряжение должно быть утверждено");
    var rows =
        db.query(
            "select * from budget_lines where id=? and work_order_id=? for update",
            (rs, n) ->
                new Line(
                    lineId,
                    orderId,
                    rs.getBigDecimal("limit_amount")
                        .subtract(rs.getBigDecimal("reserved_amount"))
                        .subtract(rs.getBigDecimal("spent_amount")),
                    rs.getString("category")),
            lineId,
            orderId);
    if (rows.isEmpty()) throw error(404, "Статья сметы не найдена");
    return rows.getFirst();
  }

  public void reserve(UUID lineId, BigDecimal amount) {
    if (db.update(
            "update budget_lines set reserved_amount=reserved_amount+? where id=? and"
                + " limit_amount-reserved_amount-spent_amount>=?",
            amount,
            lineId,
            amount)
        != 1) throw error(409, "Недостаточно свободного бюджета");
  }

  public void settle(UUID lineId, BigDecimal amount, boolean spent) {
    int count =
        db.update(
            "update budget_lines set reserved_amount=reserved_amount-?, spent_amount=spent_amount+?"
                + " where id=? and reserved_amount>=?",
            amount,
            spent ? amount : BigDecimal.ZERO,
            lineId,
            amount);
    if (count != 1) throw new IllegalStateException("Budget reservation invariant violated");
  }

  private void audit(UUID id, String status, UUID actor, String reason) {
    db.update(
        "insert into work_order_status_history(id,work_order_id,status,changed_by,reason)"
            + " values(?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        status,
        actor,
        reason);
  }

  private static ResponseStatusException error(int code, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(code), message);
  }
}
