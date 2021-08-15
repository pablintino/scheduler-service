package com.pablintino.schedulerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationError;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDataMap;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class JobParamsEncoder implements IJobParamsEncoder {

    private static final String SCHEDULER_JOB_PROPERTY_NAME = "__sch-props";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> encodeJobParameters(Task task, Endpoint endpoint) throws SchedulerValidationError {

        SchedulerJobData jobData = new SchedulerJobData(
                task.id(),
                task.key(),
                endpoint.callbackUrl(),
                endpoint.callbackType()
        );

        try {
            return Collections.singletonMap(SCHEDULER_JOB_PROPERTY_NAME, objectMapper.writeValueAsString(jobData));
        } catch (JsonProcessingException ex) {
            throw new SchedulerValidationError("Cannot create internal json datamap", ex);
        }
    }

    @Override
    public SchedulerJobData extractDecodeJobParameters(JobDataMap jobDataMap) throws SchedulerValidationError {
        if (!jobDataMap.containsKey(SCHEDULER_JOB_PROPERTY_NAME)) {
            throw new SchedulerValidationError("JobDataMap doesn't contain the internal scheduler property");
        }
        try {
            SchedulerJobData schedulerJobData = objectMapper.readValue(
                    jobDataMap.getString(SCHEDULER_JOB_PROPERTY_NAME),
                    SchedulerJobData.class
            );
            jobDataMap.remove(SCHEDULER_JOB_PROPERTY_NAME);
            return schedulerJobData;
        } catch (JsonProcessingException ex) {
            throw new SchedulerValidationError("Cannot decode internal json datamap", ex);
        }
    }
}
