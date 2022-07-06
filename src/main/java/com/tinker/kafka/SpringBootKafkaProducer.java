package com.tinker.kafka;

import com.tinker.business.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class SpringBootKafkaProducer implements Consumer<Message> {

  @Value("${spring.kafka.producer.topic}")
  private String outgoingTopic;

  private KafkaTemplate<String, String> kafkaTemplate;

  @Autowired
  SpringBootKafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void accept(Message message) {
    kafkaTemplate.send(outgoingTopic, message.key(), message.value());
  }
}
