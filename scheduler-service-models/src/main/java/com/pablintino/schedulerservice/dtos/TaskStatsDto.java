package com.pablintino.schedulerservice.dtos;

import lombok.Data;

import java.time.Instant;

@Data
public class TaskStatsDto {
  private Instant lastTriggerTime;

  private Long executions;

  private Long failures;

  private Instant lastFailureTime;
}
