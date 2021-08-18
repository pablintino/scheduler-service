package com.pablintino.schedulerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDataMap;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JobParamsEncoder implements IJobParamsEncoder {

    private static final String SCHEDULER_JOB_PROPERTY_NAME = "__sch-props";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> encodeJobParameters(Task task, Endpoint endpoint) {

        SchedulerJobData jobData = new SchedulerJobData(
                task.id(),
                task.key(),
                endpoint.callbackUrl(),
                endpoint.callbackType()
        );

        try {
            return Collections.singletonMap(SCHEDULER_JOB_PROPERTY_NAME, objectMapper.writeValueAsString(jobData));
        } catch (JsonProcessingException ex) {
            throw new SchedulerValidationException("Cannot create internal json datamap", ex);
        }
    }

    @Override
    public SchedulerJobData extractDecodeJobParameters(JobDataMap jobDataMap) {
        if (!jobDataMap.containsKey(SCHEDULER_JOB_PROPERTY_NAME)) {
            throw new SchedulerValidationException("JobDataMap doesn't contain the internal scheduler property");
        }
        try {
            SchedulerJobData schedulerJobData = objectMapper.readValue(
                    jobDataMap.getString(SCHEDULER_JOB_PROPERTY_NAME),
                    SchedulerJobData.class
            );
            jobDataMap.remove(SCHEDULER_JOB_PROPERTY_NAME);
            return schedulerJobData;
        } catch (JsonProcessingException ex) {
            throw new SchedulerValidationException("Cannot decode internal json datamap", ex);
        }
    }

    @Override
    public Map<String, Object> removeJobParameters(Map<String, Object> jobDataMap) {
        if(jobDataMap != null){
            Map<String, Object> dataMap = jobDataMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            dataMap.remove(SCHEDULER_JOB_PROPERTY_NAME);
            return dataMap;
        }
        return jobDataMap;
    }
}
