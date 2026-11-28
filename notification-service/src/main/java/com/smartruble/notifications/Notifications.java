package com.smartruble.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
class Inbox {
  private final JdbcTemplate db;
  private final ObjectMapper mapper;

  Inbox(JdbcTemplate db, ObjectMapper mapper) {
    this.db = db;
    this.mapper = mapper;
  }

  @KafkaListener(topics = "payment-events")
  @Transactional
  public void consume(String json) throws Exception {
    var event = mapper.readTree(json);
    db.update(
        "insert into notifications(id,payment_id,status,message) values(?,?,?,?) on conflict(id) do"
            + " nothing",
        UUID.fromString(event.required("eventId").asText()),
        UUID.fromString(event.required("paymentId").asText()),
        event.required("status").asText(),
        event.required("message").asText());
  }
}

@Configuration
class KafkaConfiguration {
  @Bean
  NewTopic events() {
    return TopicBuilder.name("payment-events").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic deadLetters() {
    return TopicBuilder.name("payment-events.DLT").partitions(1).replicas(1).build();
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> template) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumers);
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) -> new TopicPartition("payment-events.DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3)));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return factory;
  }

  @Bean
  ProducerFactory<String, String> producerFactory(
      @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}")
          String servers) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            "bootstrap.servers",
            servers,
            "key.serializer",
            org.apache.kafka.common.serialization.StringSerializer.class,
            "value.serializer",
            org.apache.kafka.common.serialization.StringSerializer.class));
  }

  @Bean
  KafkaTemplate<String, String> template(ProducerFactory<String, String> factory) {
    return new KafkaTemplate<>(factory);
  }
}

@RestController
class NotificationController {
  private final JdbcTemplate db;

  NotificationController(JdbcTemplate db) {
    this.db = db;
  }

  @GetMapping("/api/v1/notifications")
  Object list(
      @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Roles") String roles) {
    if (Arrays.stream(roles.split(","))
        .noneMatch(r -> Set.of("ROLE_ADMIN", "ROLE_FINANCE_DIRECTOR").contains(r)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    return db.queryForList("select * from notifications order by received_at desc,id limit 100");
  }
}
