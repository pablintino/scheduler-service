package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDetail;
import org.quartz.Trigger;

public record DummyTaskDataModels (Task task, Endpoint endpoint, Trigger trigger, JobDetail jobDetail){
}
