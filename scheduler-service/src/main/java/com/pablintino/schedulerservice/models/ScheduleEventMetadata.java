package com.pablintino.schedulerservice.models;

import lombok.Data;

import java.time.Instant;

@Data
public class ScheduleEventMetadata{

    private Instant triggerTime;
    private int attempt = 0;
}
