package com.tinker.business.internal;

import java.util.function.Function;

public final class StringReverser implements Function<String, String> {
  @Override
  public String apply(String s) {
    return new StringBuilder(s).reverse().toString();
  }
}
