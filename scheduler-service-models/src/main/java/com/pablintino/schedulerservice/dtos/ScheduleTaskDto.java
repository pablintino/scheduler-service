package com.pablintino.schedulerservice.dtos;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

@Data
public class ScheduleTaskDto {
  private String taskIdentifier;
  private String taskKey;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private ZonedDateTime triggerTime;

  private String cronExpression;
  private Object taskData;
}
