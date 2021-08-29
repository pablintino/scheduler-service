package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.quartz.JobDetail;
import org.quartz.Trigger;

@Getter
@RequiredArgsConstructor
public class DummyTaskDataModels {

    private final Task task;
    private final Endpoint endpoint;
    private final Trigger trigger;
    private final JobDetail jobDetail;

}
