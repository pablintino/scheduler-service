package com.pablintino.schedulerservice.models;

import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Map;

public record Task(String id, String key, LocalDateTime triggerTime,
                   Map<String, Object> taskData) {

    public Task {
        Assert.hasLength(id, "id cannot be null or empty");
        Assert.hasLength(key, "key cannot be null or empty");
        Assert.notNull(triggerTime, "triggerTime cannot be null or empty");
    }
}
