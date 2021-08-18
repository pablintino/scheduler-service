package com.pablintino.schedulerservice.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum CallbackMethodTypeDto {
    REST,
    AMQP;

    @JsonCreator
    public static CallbackMethodTypeDto create(String value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        for (CallbackMethodTypeDto v : values()) {
            if (value.toUpperCase(Locale.ROOT).equals(v.toString())) {
                return v;
            }
        }
        throw new IllegalArgumentException();
    }
}
