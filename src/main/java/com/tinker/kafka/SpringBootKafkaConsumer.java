package com.tinker.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpringBootKafkaConsumer {

  private final Reverser businessOperation = new Reverser();

  @Autowired
  private SpringBootKafkaProducer producer;

  @KafkaListener(topics = "#{'${spring.kafka.consumer.topic}'}")
  public void consume(ConsumerRecord<String, String> record) {
    Optional.of(new Message(record.key(), record.value()))
            .map(businessOperation)
            .stream()
            .forEach(producer);
  }
}
