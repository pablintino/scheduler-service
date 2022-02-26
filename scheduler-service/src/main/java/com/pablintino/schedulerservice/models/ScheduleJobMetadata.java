package com.pablintino.schedulerservice.models;

import lombok.Data;

import java.time.Instant;

@Data
public class ScheduleJobMetadata {

  private Instant triggerTime;
  private Instant lastFailureTime;
  private long notificationAttempt = 0;
  private long failures = 0;
  private long executions = 0;
}
