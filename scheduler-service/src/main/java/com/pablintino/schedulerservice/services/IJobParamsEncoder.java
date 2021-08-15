package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationError;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDataMap;

import java.util.Map;

public interface IJobParamsEncoder {

    Map<String, Object> encodeJobParameters(Task task, Endpoint endpoint) throws SchedulerValidationError;
    SchedulerJobData extractDecodeJobParameters(JobDataMap jobDataMap) throws SchedulerValidationError;
}
