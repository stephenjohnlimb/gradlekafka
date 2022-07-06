package com.tinker.kafka;

import java.util.function.Function;

/** A simple business function that reverses the value of the message, leaves the key as is. */
public class Reverser implements Function<Message, Message> {

  @Override
  public Message apply(Message message) {
    return new Message(message.key(), new StringBuilder(message.value()).reverse().toString());
  }
}
