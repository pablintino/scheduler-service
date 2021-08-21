package com.pablintino.schedulerservice.dtos;

import lombok.Builder;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.ZonedDateTime;
import java.util.Map;

@Data
public class ScheduleRequestDto {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9-]*$")
    private String taskIdentifier;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9-.]*$")

    private String taskKey;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime triggerTime;

    private String cronExpression;

    @NotNull
    private CallbackDescriptorDto callbackDescriptor;

    private Map<String, Object> taskData;
}
