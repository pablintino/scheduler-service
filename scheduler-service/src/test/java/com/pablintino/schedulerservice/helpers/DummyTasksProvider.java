package com.pablintino.schedulerservice.helpers;

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
import java.util.Collections;
import java.util.Date;
import java.util.List;


@Component
@RequiredArgsConstructor
public class DummyTasksProvider {

    private final IJobParamsEncoder jobParamsEncoder;

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
                Collections.emptyMap());

        Endpoint dummyEndpoint = new Endpoint(CallbackType.AMQP, null);

        JobDataMap dataMap = new JobDataMap();
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(dummyTask, dummyEndpoint));

        JobDetail job = JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(dummyTask.getId(), dummyTask.getKey())
                .setJobData(dataMap)
                .build();

        return new DummyTaskDataModels(dummyTask, dummyEndpoint, trigger, job);
    }

    public void validateSimpleValidJob(DummyTaskDataModels dummyTaskDataModels, JobExecutionContext jobExecutionContext){
        Assertions.assertNotNull(jobExecutionContext);
        SchedulerJobData schedulerJobData = jobParamsEncoder.extractDecodeJobParameters(jobExecutionContext.getJobDetail().getJobDataMap());
        validateSimpleValidJobParams(dummyTaskDataModels, schedulerJobData, jobExecutionContext.getFireTime().toInstant());

    }

    public void validateSimpleValidJob(DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, JobDataMap jobDataMap, Instant callTime){
        validateSimpleValidJobParams(dummyTaskDataModels, jobData, callTime);
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

    private static void validateSimpleValidJobParams(DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, Instant callTime){
        validateCommonSchedulerDataParams(dummyTaskDataModels, jobData, callTime);
        // TODO Review this tolerance
        Assertions.assertTrue(callTime.toEpochMilli() - dummyTaskDataModels.getTask().getTriggerTime().toInstant().toEpochMilli() <= 50);
    }

    private static void validateCommonSchedulerDataParams(DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, Instant callTime) {
        Assertions.assertEquals(dummyTaskDataModels.getTask().getId(), jobData.getTaskId());
        Assertions.assertEquals(dummyTaskDataModels.getTask().getKey(), jobData.getKey());
        Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackUrl(), jobData.getCallbackUrl());
        Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackType(), jobData.getType());
        Assertions.assertTrue(dummyTaskDataModels.getTask().getTriggerTime().toInstant().isBefore(callTime));
    }
}
