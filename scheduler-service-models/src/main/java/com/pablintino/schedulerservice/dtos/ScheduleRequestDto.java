package com.pablintino.schedulerservice.dtos;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;
import java.util.Map;

@Data
public class ScheduleRequestDto {
    private String taskIdentifier;
    private String taskKey;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime triggerTime;
    private String cronExpression;
    private CallbackDescriptorDto callbackDescriptor;
    private Map<String, Object> taskData;
}
