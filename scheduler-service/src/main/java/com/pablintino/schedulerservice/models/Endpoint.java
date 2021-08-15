package com.pablintino.schedulerservice.models;

import org.springframework.util.Assert;

public record Endpoint(CallbackType callbackType, String callbackUrl) {
    public Endpoint {
        Assert.notNull(callbackType, "callbackType cannot be null");
    }
}
