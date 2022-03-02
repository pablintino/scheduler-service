package com.pablintino.schedulerservice.dtos;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.ZonedDateTime;

@Data
public class ScheduleRequestDto {

  @NotBlank
  @Pattern(regexp = "^[a-zA-Z0-9-]*$")
  private String taskIdentifier;

  @NotBlank
  @Pattern(regexp = "^[a-zA-Z0-9-.]*$")
  private String taskKey;

  private ZonedDateTime triggerTime;

  private String cronExpression;

  @NotNull private CallbackDescriptorDto callbackDescriptor;

  private Object taskData;
}
