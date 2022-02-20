package com.pablintino.schedulerservice.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;

@Getter
public class SchedulerJobData {

  private final String taskId;
  private final String key;
  private final String callbackUrl;
  private final CallbackType type;
  private final ScheduleJobMetadata metadata;

  public SchedulerJobData(
      @JsonProperty("taskId") String taskId,
      @JsonProperty("key") String key,
      @JsonProperty("callbackUrl") String callbackUrl,
      @JsonProperty("type") CallbackType type,
      @JsonProperty("metadata") ScheduleJobMetadata metadata) {
    Assert.hasLength(taskId, "taskId cannot be null or empty");
    Assert.hasLength(key, "key cannot be null or empty");
    Assert.notNull(type, "type cannot be null");
    this.taskId = taskId;
    this.key = key;
    this.callbackUrl = callbackUrl;
    this.type = type;
    this.metadata = metadata;
  }
}
