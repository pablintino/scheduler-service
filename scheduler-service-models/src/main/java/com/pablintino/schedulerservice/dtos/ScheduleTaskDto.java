package com.pablintino.schedulerservice.dtos;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class ScheduleTaskDto {
  private String taskIdentifier;
  private String taskKey;

  private ZonedDateTime triggerTime;

  private String cronExpression;
  private Object taskData;
}
