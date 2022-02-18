package com.pablintino.schedulerservice.dtos;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Data
public class TaskStatsDto {
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant lastTriggerTime;

  private Long executions;

  private Long failures;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant lastFailureTime;
}
