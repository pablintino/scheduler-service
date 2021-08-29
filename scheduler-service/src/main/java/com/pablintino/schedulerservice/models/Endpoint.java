package com.pablintino.schedulerservice.models;

import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

@Getter
@ToString
public class Endpoint {

    private final CallbackType callbackType;
    private final String callbackUrl;

    public Endpoint(CallbackType callbackType, String callbackUrl) {
        Assert.notNull(callbackType, "callbackType cannot be null");
        this.callbackType = callbackType;
        this.callbackUrl = callbackUrl;
    }
}
