package com.pablintino.schedulerservice.models;

import org.springframework.util.Assert;

public record SchedulerJobData(String taskId, String key, String callbackUrl, CallbackType type) {
    public SchedulerJobData{
        Assert.hasLength(taskId, "taskId cannot be null or empty");
        Assert.hasLength(key, "key cannot be null or empty");
        Assert.notNull(type, "type cannot be null");
    }
}
