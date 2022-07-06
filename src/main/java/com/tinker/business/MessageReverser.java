package com.tinker.business;

import com.tinker.business.internal.StringReverser;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Function;

/** A simple business function that reverses the value of the message, leaves the key as is. */
public final class MessageReverser implements Function<Message, Message> {

  private final Function<String, String> reverser = new StringReverser();

  @Override
  public Message apply(Message message) {
    return new Message(message.key(), reverser.apply(message.value()));
  }
}
