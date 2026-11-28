package com.smartruble.billing.payments;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true")
class OutboxPublisher {
  private final JdbcTemplate db;
  private final KafkaTemplate<String, String> kafka;

  @Scheduled(fixedDelay = 1500)
  @Transactional
  public void publish() {
    // One ordered publisher per database, safe even with multiple billing instances.
    Boolean leader = db.queryForObject("select pg_try_advisory_xact_lock(722316)", Boolean.class);
    if (!Boolean.TRUE.equals(leader)) return;
    var rows =
        db.queryForList(
            "select * from payment_outbox where published_at is null order by sequence limit 50 for"
                + " update");
    for (var event : rows) {
      try {
        kafka
            .send(
                "payment-events",
                event.get("payment_id").toString(),
                event.get("payload").toString())
            .get(5, TimeUnit.SECONDS);
        db.update("update payment_outbox set published_at=now() where id=?", event.get("id"));
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        LoggerFactory.getLogger(getClass()).warn("Kafka unavailable; unpublished events retained");
        break;
      }
    }
  }
}
