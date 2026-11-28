package com.smartruble.billing;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartruble.billing.access.Caller;
import com.smartruble.billing.payments.Payments;
import com.smartruble.billing.workorders.WorkOrders;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {"app.jobs.enabled=false", "app.events.enabled=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PaymentWorkflowTest {
  @Autowired WorkOrders orders;
  @Autowired Payments payments;
  @Autowired JdbcTemplate db;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  static HttpServer bank;
  static final Map<UUID, String> transfers = new ConcurrentHashMap<>();
  static final AtomicBoolean loseResponse = new AtomicBoolean();
  static final AtomicBoolean reject = new AtomicBoolean();
  final Caller admin = new Caller(UUID.randomUUID(), Set.of("ROLE_ADMIN"));
  UUID company, order, line;

  @DynamicPropertySource
  static void bankUrl(DynamicPropertyRegistry registry) throws Exception {
    bank = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    bank.setExecutor(
        Executors.newCachedThreadPool(
            r -> {
              Thread t = new Thread(r);
              t.setDaemon(true);
              return t;
            }));
    bank.createContext(
        "/internal/transfers",
        exchange -> {
          var mapper = new ObjectMapper();
          var command = mapper.readTree(exchange.getRequestBody());
          UUID id = UUID.fromString(command.get("paymentId").asText());
          String result =
              transfers.computeIfAbsent(
                  id,
                  key ->
                      "{\"paymentId\":\""
                          + id
                          + "\",\"reference\":\""
                          + UUID.randomUUID()
                          + "\",\"status\":\""
                          + (reject.getAndSet(false) ? "FAILED" : "SUCCEEDED")
                          + "\"}");
          if (loseResponse.getAndSet(false)) {
            exchange.close();
            return;
          }
          byte[] bytes = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    bank.start();
    registry.add("app.bank.url", () -> "http://127.0.0.1:" + bank.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    bank.stop(0);
  }

  @BeforeEach
  void setup() {
    transfers.clear();
    loseResponse.set(false);
    reject.set(false);
    db.update("delete from payment_outbox");
    db.update("delete from payment_status_history");
    db.update("delete from approval_tasks");
    db.update("delete from payment_requests");
    company = UUID.randomUUID();
    String inn = String.format("%010d", Math.abs(company.getLeastSignificantBits() % 10000000000L));
    db.update(
        "insert into"
            + " counterparties(id,type,name,inn,verification_status,created_at,updated_at,created_by)"
            + " values(?,'LEGAL_ENTITY','Тестовый подрядчик',?,'VERIFIED',now(),now(),?)",
        company,
        inn,
        admin.id());
    db.update(
        "insert into"
            + " counterparty_bank_accounts(id,counterparty_id,account_number,bik,bank_name,is_primary,active,created_at)"
            + " values(?,?,'40702810900000000001','044525225','Mock',true,true,now())",
        UUID.randomUUID(),
        company);
    var created =
        orders.create(
            new WorkOrders.Create(
                "Ремонт",
                "Офис",
                company,
                List.of(new WorkOrders.Budget("MATERIALS", new BigDecimal("100000.00")))),
            admin);
    order = (UUID) created.get("id");
    line =
        db.queryForObject("select id from budget_lines where work_order_id=?", UUID.class, order);
    orders.transition(order, "ACTIVE", admin);
  }

  Payments.Create command(String amount) {
    return new Payments.Create(order, line, company, new BigDecimal(amount), "Материалы");
  }

  UUID create(String amount) {
    return (UUID) payments.create(UUID.randomUUID(), command(amount), admin).get("id");
  }

  BigDecimal value(String column) {
    return db.queryForObject(
        "select " + column + " from budget_lines where id=?", BigDecimal.class, line);
  }

  void dispatch(UUID id) {
    assertThat(payments.prepareNext()).isEqualTo(id);
    payments.deliver(id);
  }

  @Test
  void approvedPaymentAndIdempotentCreation() {
    UUID key = UUID.randomUUID();
    var first = payments.create(key, command("1000.00"), admin);
    assertThat(payments.create(key, command("1000.00"), admin).get("id"))
        .isEqualTo(first.get("id"));
    assertThatThrownBy(() -> payments.create(key, command("2000.00"), admin))
        .isInstanceOf(ResponseStatusException.class);
    dispatch((UUID) first.get("id"));
    assertThat(value("spent_amount")).isEqualByComparingTo("1000.00");
    assertThat(value("reserved_amount")).isZero();
    assertThat(transfers).hasSize(1);
    assertThat(db.queryForObject("select count(*) from payment_outbox", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void concurrentRequestsCannotOverspend() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Map<String, Object>> task =
          () -> {
            start.await();
            return payments.create(UUID.randomUUID(), command("60000.00"), admin);
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(List.of(a.get().get("status"), b.get().get("status")))
          .containsExactlyInAnyOrder("PENDING_APPROVAL", "BLOCKED");
    }
    assertThat(value("reserved_amount")).isEqualByComparingTo("60000.00");
  }

  @Test
  void refusalReleasesReservationAndCannotBeDecidedTwice() {
    UUID id = create("60000.00");
    payments.approve(id, new Payments.Approval(false, "Не подтверждены объёмы"), admin);
    assertThat(value("reserved_amount")).isZero();
    assertThat(payments.details(id, admin).get("status")).isEqualTo("REJECTED");
    assertThatThrownBy(
            () -> payments.approve(id, new Payments.Approval(true, "Позднее решение"), admin))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void financeApprovalAllowsOneSettlement() {
    UUID id = create("60000.00");
    var director = new Caller(UUID.randomUUID(), Set.of("ROLE_FINANCE_DIRECTOR"));
    assertThat(
            payments
                .approve(id, new Payments.Approval(true, "Смета подтверждена"), director)
                .get("status"))
        .isEqualTo("READY");
    dispatch(id);
    payments.deliver(id);
    assertThat(value("spent_amount")).isEqualByComparingTo("60000.00");
    assertThat(value("reserved_amount")).isZero();
    assertThat(transfers).hasSize(1);
    assertThat(
            db.queryForObject(
                "select decided_by from approval_tasks where payment_id=?", UUID.class, id))
        .isEqualTo(director.id());
  }

  @Test
  void changedRequisitesBeforeDispatchReleaseBudget() {
    UUID id = create("1000.00");
    db.update(
        "update counterparty_bank_accounts set account_number='40702810900000000002' where"
            + " counterparty_id=?",
        company);
    assertThat(payments.prepareNext()).isNull();
    assertThat(payments.details(id, admin).get("status")).isEqualTo("FAILED");
    assertThat(value("reserved_amount")).isZero();
    assertThat(transfers).isEmpty();
  }

  @Test
  void bankDeclineReleasesReservation() {
    UUID id = create("1000.00");
    reject.set(true);
    dispatch(id);
    assertThat(payments.details(id, admin).get("status")).isEqualTo("FAILED");
    assertThat(value("spent_amount")).isZero();
    assertThat(value("reserved_amount")).isZero();
  }

  @Test
  void lostResponseKeepsReserveAndRetryDoesNotDebitTwice() {
    UUID id = create("1000.00");
    loseResponse.set(true);
    dispatch(id);
    assertThat(payments.details(id, admin).get("status")).isEqualTo("PROCESSING");
    assertThat(value("reserved_amount")).isEqualByComparingTo("1000.00");
    // Make retry due without sleeping. Dispatch reuses the committed PROCESSING intent.
    db.update("update payment_requests set updated_at=now()-interval '10 seconds' where id=?", id);
    dispatch(id);
    assertThat(payments.details(id, admin).get("status")).isEqualTo("SUCCEEDED");
    assertThat(transfers).hasSize(1);
    assertThat(value("spent_amount")).isEqualByComparingTo("1000.00");
  }

  @Test
  void dispatchIntentSurvivesInterruptedWorker() {
    UUID id = create("1000.00");
    assertThat(payments.prepareNext()).isEqualTo(id);
    assertThat(payments.details(id, admin).get("status")).isEqualTo("PROCESSING");
    db.update("update payment_requests set updated_at=now()-interval '10 seconds' where id=?", id);
    dispatch(id);
    assertThat(transfers).hasSize(1);
  }

  @Test
  void blockedRecipientAndObjectAuthorization() {
    db.update("update counterparties set verification_status='BLOCKED' where id=?", company);
    assertThat(payments.details(create("1000.00"), admin).get("status")).isEqualTo("BLOCKED");
    assertThat(value("reserved_amount")).isZero();
    var stranger = new Caller(UUID.randomUUID(), Set.of("ROLE_CONTRACTOR"));
    assertThatThrownBy(() -> orders.details(order, stranger))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> payments.create(UUID.randomUUID(), command("1000.00"), stranger))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void pendingReservationPreventsClosingOrder() {
    UUID id = create("60000.00");
    assertThatThrownBy(() -> orders.transition(order, "CLOSED", admin))
        .isInstanceOf(ResponseStatusException.class);
    payments.approve(id, new Payments.Approval(false, "Отмена закупки"), admin);
    assertThat(orders.transition(order, "CLOSED", admin).get("status")).isEqualTo("CLOSED");
  }

  @Test
  void httpEntryPointsValidateIdentityRolesAndMoney() throws Exception {
    mvc.perform(get("/api/v1/billing/workorders")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/billing/payments")
                .header("X-User-Id", UUID.randomUUID())
                .header("X-User-Roles", "ROLE_EMPLOYEE")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(json.writeValueAsBytes(command("100.00"))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/billing/payments")
                .header("X-User-Id", admin.id())
                .header("X-User-Roles", "ROLE_ADMIN")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(json.writeValueAsBytes(command("-1.00"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v1/billing/workorders/" + order)
                .header("X-User-Id", admin.id())
                .header("X-User-Roles", "ROLE_ADMIN"))
        .andExpect(status().isOk());
  }
}
