package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.models.SchedulerJobData;
import org.quartz.JobDataMap;

public interface ICallbackService {

    void executeCallback(SchedulerJobData jobData, JobDataMap jobDataMap);
}
