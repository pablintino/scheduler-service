package com.pablintino.schedulerservice.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum CallbackMethodTypeDto {
  HTTP,
  AMQP;

  @JsonCreator
  public static CallbackMethodTypeDto create(String value) {
    if (value != null) {
      for (CallbackMethodTypeDto v : values()) {
        if (value.toUpperCase(Locale.ROOT).equals(v.toString())) {
          return v;
        }
      }
    }
    throw new IllegalArgumentException("Invalid callback type value");
  }
}
