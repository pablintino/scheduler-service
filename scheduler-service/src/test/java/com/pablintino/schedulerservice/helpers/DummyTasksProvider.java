package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.dtos.CallbackDescriptorDto;
import com.pablintino.schedulerservice.dtos.CallbackMethodTypeDto;
import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;


@Component
@RequiredArgsConstructor
public class DummyTasksProvider {

    private final IJobParamsEncoder jobParamsEncoder;


    public Map<String, Object> createSimpleJobDataMap(){
        Map<String, Object> dummyMap = new HashMap<>();
        dummyMap.put("key1-int", 1);
        dummyMap.put("key1-double", 1.2);
        dummyMap.put("key1-str", "test");
        dummyMap.put("key1-bool", true);
        return dummyMap;
    }

    public DummyTaskDataModels createSimpleValidJob(String name, long triggerTime) {
        Date startDate = Date.from(Instant.now().plusMillis(triggerTime));
        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(name, "it-test")
                .startAt(startDate)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();

        Task dummyTask = new Task(name, "it-test",
                ZonedDateTime.ofInstant(startDate.toInstant(), ZoneOffset.UTC),
                null,
                createSimpleJobDataMap());

        Endpoint dummyEndpoint = new Endpoint(CallbackType.AMQP, null);

        JobDataMap dataMap = new JobDataMap();
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(dummyTask, dummyEndpoint));
        dataMap.putAll(dummyTask.getTaskData());

        JobDetail job = JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(dummyTask.getId(), dummyTask.getKey())
                .setJobData(dataMap)
                .build();

        ScheduleRequestDto scheduleRequestDto = new ScheduleRequestDto();
        scheduleRequestDto.setTaskData(dataMap.getWrappedMap());
        scheduleRequestDto.setTaskKey(dummyTask.getKey());
        scheduleRequestDto.setTaskIdentifier(dummyTask.getId());
        scheduleRequestDto.setTriggerTime(dummyTask.getTriggerTime());
        scheduleRequestDto.setCronExpression(dummyTask.getCronExpression());
        CallbackDescriptorDto callbackDescriptorDto = new CallbackDescriptorDto();
        callbackDescriptorDto.setEndpoint(dummyEndpoint.getCallbackUrl());
        callbackDescriptorDto.setType(CallbackMethodTypeDto.valueOf(dummyEndpoint.getCallbackType().toString()));
        scheduleRequestDto.setCallbackDescriptor(callbackDescriptorDto);

        return new DummyTaskDataModels(dummyTask, dummyEndpoint, trigger, job,scheduleRequestDto);
    }

    public void validateSimpleValidReattemptedJob(DummyTaskDataModels dummyTaskDataModels, List<DummyCallbackService.CallbackCallEntry> executions, int retries, long attemptsDelay){
        Assertions.assertEquals(retries + 1, executions.size());
        for(int index = 0; index < executions.size(); index++){
            DummyCallbackService.CallbackCallEntry currentEntry = executions.get(index);
            validateCommonSchedulerDataParams(dummyTaskDataModels, currentEntry.getJobData(), currentEntry.getScheduleEventMetadata().getTriggerTime());
            Assertions.assertEquals(index + 1, currentEntry.getScheduleEventMetadata().getAttempt());
            if(index > 0){
                DummyCallbackService.CallbackCallEntry previousEntry = executions.get(index - 1);
                Instant expectedRetriggerTime = previousEntry.getScheduleEventMetadata().getTriggerTime().plus(attemptsDelay, ChronoUnit.MILLIS);
                Instant retriggerInstant = currentEntry.getScheduleEventMetadata().getTriggerTime();
                if(expectedRetriggerTime.plus(50, ChronoUnit.MILLIS).isBefore(retriggerInstant) || expectedRetriggerTime.minus(50, ChronoUnit.MILLIS).isAfter(retriggerInstant)){
                    Assertions.fail("Retrigger instant of a reattempt is out of time");
                }
            }
        }
    }

    public void validateSimpleValidJob(DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, JobDataMap jobDataMap, Instant callTime){
        validateCommonSchedulerDataParams(dummyTaskDataModels, jobData, callTime);
        // TODO Review this tolerance
        Assertions.assertTrue(callTime.toEpochMilli() - dummyTaskDataModels.getTask().getTriggerTime().toInstant().toEpochMilli() <= 50);
        Assertions.assertEquals(dummyTaskDataModels.getTask().getTaskData(), jobDataMap.getWrappedMap());
    }

    private static void validateCommonSchedulerDataParams(DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, Instant callTime) {
        Assertions.assertEquals(dummyTaskDataModels.getTask().getId(), jobData.getTaskId());
        Assertions.assertEquals(dummyTaskDataModels.getTask().getKey(), jobData.getKey());
        Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackUrl(), jobData.getCallbackUrl());
        Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackType(), jobData.getType());
        Assertions.assertTrue(dummyTaskDataModels.getTask().getTriggerTime().toInstant().isBefore(callTime));
    }
}
