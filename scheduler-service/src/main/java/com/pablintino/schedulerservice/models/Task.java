package com.pablintino.schedulerservice.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

import java.time.ZonedDateTime;

@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include private final String id;

  @EqualsAndHashCode.Include private final String key;

  private final ZonedDateTime triggerTime;
  private final String cronExpression;

  @ToString.Exclude private final Object taskData;

  public Task(
      String id, String key, ZonedDateTime triggerTime, String cronExpression, Object taskData) {
    Assert.hasLength(id, "id cannot be null or empty");
    Assert.hasLength(key, "key cannot be null or empty");
    this.id = id;
    this.key = key;
    this.triggerTime = triggerTime;
    this.cronExpression = cronExpression;
    this.taskData = taskData;
  }
}
