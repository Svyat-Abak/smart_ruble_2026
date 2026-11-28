package com.smartruble.notifications;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
class DeliveryTest {
  @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.9.1");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", pg::getJdbcUrl);
    r.add("spring.datasource.username", pg::getUsername);
    r.add("spring.datasource.password", pg::getPassword);
    r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
  }

  @Autowired KafkaTemplate<String, String> producer;
  @Autowired JdbcTemplate db;

  @Test
  void duplicateEventIsStoredOnceAndMalformedEventGoesToDlt() throws Exception {
    UUID event = UUID.randomUUID();
    String key = UUID.randomUUID().toString();
    String payload =
        "{\"eventId\":\""
            + event
            + "\",\"paymentId\":\""
            + key
            + "\",\"status\":\"SUCCEEDED\",\"message\":\"Оплачено\"}";
    producer.send("payment-events", key, payload).get();
    producer.send("payment-events", key, payload).get();
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        db.queryForObject(
                            "select count(*) from notifications where id=?", Integer.class, event))
                    .isOne());
    var properties = new Properties();
    properties.put("bootstrap.servers", kafka.getBootstrapServers());
    properties.put("group.id", "test-" + event);
    properties.put("auto.offset.reset", "earliest");
    properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.put(
        "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    producer.send("payment-events", key, "invalid-json").get();
    try (var consumer = new KafkaConsumer<String, String>(properties)) {
      consumer.subscribe(List.of("payment-events.DLT"));
      await()
          .atMost(Duration.ofSeconds(30))
          .until(
              () -> {
                for (var record : consumer.poll(Duration.ofMillis(500)))
                  if (record.key().equals(key) && record.value().equals("invalid-json"))
                    return true;
                return false;
              });
    }
    assertThat(
            db.queryForObject(
                "select count(*) from notifications where id=?", Integer.class, event))
        .isOne();
  }
}
