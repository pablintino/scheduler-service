package com.pablintino.schedulerservice.models;

import org.springframework.util.Assert;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

public record Task(String id, String key, ZonedDateTime triggerTime,
                   String cronExpression, Map<String, Object> taskData) {

    public Task(String id, String key, ZonedDateTime triggerTime, String cronExpression,
                Map<String, Object> taskData) {
        Assert.hasLength(id, "id cannot be null or empty");
        Assert.hasLength(key, "key cannot be null or empty");
        this.id = id;
        this.key = key;
        this.triggerTime = triggerTime;
        this.cronExpression = cronExpression;
        this.taskData = taskData != null ? taskData : new HashMap<>();
    }
}
