package com.pablintino.schedulerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDataMap;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JobParamsEncoder implements IJobParamsEncoder {

    private static final String SCHEDULER_JOB_PROPERTY_NAME = "__sch-props";
    private static final String SCHEDULER_JOB_FIRE_TIME = "__sch-fire-time";
    private static final String SCHEDULER_JOB_ATTEMPT = "__sch-attempt";

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
    public void encodeUpdateSchedulerEventMetadata(JobDataMap jobDataMap, ScheduleEventMetadata scheduleEventMetadata) {
        if(scheduleEventMetadata.getTriggerTime() != null){
            jobDataMap.putAsString(SCHEDULER_JOB_FIRE_TIME, scheduleEventMetadata.getTriggerTime().toEpochMilli());
        }
        jobDataMap.putAsString(SCHEDULER_JOB_ATTEMPT, scheduleEventMetadata.getAttempt());
    }

    @Override
    public ScheduleEventMetadata extractDecodeSchedulerEventMetadata(JobDataMap jobDataMap) {
        ScheduleEventMetadata scheduleEventMetadata = new ScheduleEventMetadata();

        if(jobDataMap.containsKey(SCHEDULER_JOB_FIRE_TIME)){
            scheduleEventMetadata.setTriggerTime(Instant.ofEpochMilli(jobDataMap.getLongFromString(SCHEDULER_JOB_FIRE_TIME)));
        }
        if(jobDataMap.containsKey(SCHEDULER_JOB_ATTEMPT)){
            scheduleEventMetadata.setAttempt(jobDataMap.getIntegerFromString(SCHEDULER_JOB_ATTEMPT));
        }
        jobDataMap.remove(SCHEDULER_JOB_FIRE_TIME);
        jobDataMap.remove(SCHEDULER_JOB_ATTEMPT);
        return scheduleEventMetadata;
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
        return null;
    }
}
