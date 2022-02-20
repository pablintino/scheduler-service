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
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DummyTasksProvider {

  private final IJobParamsEncoder jobParamsEncoder;

  public Map<String, Object> createSimpleJobDataMap() {
    Map<String, Object> dummyMap = new HashMap<>();
    dummyMap.put("key1-int", 1);
    dummyMap.put("key1-double", 1.2);
    dummyMap.put("key1-str", "test");
    dummyMap.put("key1-bool", true);
    return dummyMap;
  }

  public DummyTaskDataModels createSimpleValidJob(String name, long triggerTime) {

    return getDummyTaskDataModels(name, triggerTime, null);
  }

  public DummyTaskDataModels createCronValidJob(String name, long triggerTime, String cron) {

    return getDummyTaskDataModels(name, triggerTime, cron);
  }

  private DummyTaskDataModels getDummyTaskDataModels(String name, long triggerTime, String cron) {
    Date startDate = Date.from(Instant.now().plusMillis(triggerTime));
    Assertions.assertTrue(cron == null || (cron != null && StringUtils.isNotBlank(cron)));
    Assertions.assertTrue(StringUtils.isNotBlank(name));

    ScheduleBuilder triggerBuilder =
        StringUtils.isBlank(cron)
            ? SimpleScheduleBuilder.simpleSchedule()
            : CronScheduleBuilder.cronSchedule(cron);
    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(name, "it-test")
            .startAt(startDate)
            .withSchedule(triggerBuilder)
            .build();
    Task dummyTask =
        new Task(
            name,
            "it-test",
            ZonedDateTime.ofInstant(startDate.toInstant(), ZoneOffset.UTC),
            cron,
            createSimpleJobDataMap());

    Endpoint dummyEndpoint = new Endpoint(CallbackType.AMQP, null);

    JobDataMap dataMap =
        new JobDataMap(jobParamsEncoder.createEncodeJobParameters(dummyTask, dummyEndpoint));
    JobDetail job =
        JobBuilder.newJob(CallbackJob.class)
            .withIdentity(dummyTask.getId(), dummyTask.getKey())
            .setJobData(dataMap)
            .build();

    ScheduleRequestDto scheduleRequestDto = new ScheduleRequestDto();
    scheduleRequestDto.setTaskData(dummyTask.getTaskData());
    scheduleRequestDto.setTaskKey(dummyTask.getKey());
    scheduleRequestDto.setTaskIdentifier(dummyTask.getId());
    scheduleRequestDto.setTriggerTime(dummyTask.getTriggerTime());
    scheduleRequestDto.setCronExpression(dummyTask.getCronExpression());
    CallbackDescriptorDto callbackDescriptorDto = new CallbackDescriptorDto();
    callbackDescriptorDto.setEndpoint(dummyEndpoint.getCallbackUrl());
    callbackDescriptorDto.setType(
        CallbackMethodTypeDto.valueOf(dummyEndpoint.getCallbackType().toString()));
    scheduleRequestDto.setCallbackDescriptor(callbackDescriptorDto);

    return new DummyTaskDataModels(dummyTask, dummyEndpoint, trigger, job, scheduleRequestDto);
  }

  public void validateSimpleValidReattemptedNotRecoveredJob(
      DummyTaskDataModels dummyTaskDataModels,
      List<DummyCallbackService.CallbackCallEntry> executions,
      int retries,
      long attemptsDelay) {
    Assertions.assertEquals(retries + 1, executions.size());
    for (int index = 0; index < executions.size(); index++) {
      DummyCallbackService.CallbackCallEntry currentEntry = executions.get(index);
      validateCommonSchedulerDataParams(
          dummyTaskDataModels,
          currentEntry.getJobData(),
          currentEntry.getJobData().getMetadata().getLastTriggerTime());
      // TODO Improve this checks. Too basic
      Assertions.assertEquals(
          index + 1, currentEntry.getJobData().getMetadata().getNotificationAttempt());
      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getExecutions());
      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getFailures());
      Assertions.assertEquals(
          dummyTaskDataModels.getTask().getTaskData(), currentEntry.getTaskDataMap());
      Assertions.assertNotNull(currentEntry.getJobData().getMetadata().getLastFailureTime());

      if (index > 0) {
        DummyCallbackService.CallbackCallEntry previousEntry = executions.get(index - 1);
        validateRetriesTime(currentEntry, previousEntry, attemptsDelay);
      }
    }
  }

  public void validateReattemptedAndRecoveredJob(
      DummyTaskDataModels dummyTaskDataModels,
      List<DummyCallbackService.CallbackCallEntry> executions,
      int retries,
      long attemptsDelay,
      int recoverRetry) {
    Assertions.assertEquals(retries + 1, executions.size());
    for (int index = 0; index < executions.size(); index++) {
      DummyCallbackService.CallbackCallEntry currentEntry = executions.get(index);
      validateCommonSchedulerDataParams(
          dummyTaskDataModels,
          currentEntry.getJobData(),
          currentEntry.getJobData().getMetadata().getLastTriggerTime());
      // TODO Improve this checks. Too basic
      Assertions.assertEquals(
          index + 1, currentEntry.getJobData().getMetadata().getNotificationAttempt());
      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getExecutions());
      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getFailures());
      Assertions.assertEquals(
          dummyTaskDataModels.getTask().getTaskData(), currentEntry.getTaskDataMap());
      Assertions.assertNotNull(currentEntry.getJobData().getMetadata().getLastFailureTime());

      if (index > 0) {
        DummyCallbackService.CallbackCallEntry previousEntry = executions.get(index - 1);
        validateRetriesTime(currentEntry, previousEntry, attemptsDelay);
      }
    }
  }

  private static void validateRetriesTime(
      DummyCallbackService.CallbackCallEntry currentEntry,
      DummyCallbackService.CallbackCallEntry previousEntry,
      long attemptsDelay) {

    Instant expectedRetriggerTime =
        previousEntry
            .getJobData()
            .getMetadata()
            .getLastTriggerTime()
            .plus(attemptsDelay, ChronoUnit.MILLIS);
    Instant retriggerInstant = currentEntry.getJobData().getMetadata().getLastTriggerTime();
    if (expectedRetriggerTime.plus(50, ChronoUnit.MILLIS).isBefore(retriggerInstant)
        || expectedRetriggerTime.minus(50, ChronoUnit.MILLIS).isAfter(retriggerInstant)) {
      Assertions.fail("Retrigger instant of a reattempt is out of time");
    }
  }

  public void validateSimpleValidJob(
      DummyTaskDataModels dummyTaskDataModels,
      SchedulerJobData jobData,
      Map<String, Object> taskDataMap,
      Instant callTime) {
    validateCommonSchedulerDataParams(dummyTaskDataModels, jobData, callTime);
    // TODO Review this tolerance
    Assertions.assertTrue(
        callTime.toEpochMilli()
                - dummyTaskDataModels.getTask().getTriggerTime().toInstant().toEpochMilli()
            <= 500);
    Assertions.assertEquals(dummyTaskDataModels.getTask().getTaskData(), taskDataMap);
  }

  private static void validateCommonSchedulerDataParams(
      DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData, Instant callTime) {
    Assertions.assertEquals(dummyTaskDataModels.getTask().getId(), jobData.getTaskId());
    Assertions.assertEquals(dummyTaskDataModels.getTask().getKey(), jobData.getKey());
    Assertions.assertEquals(
        dummyTaskDataModels.getEndpoint().getCallbackUrl(), jobData.getCallbackUrl());
    Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackType(), jobData.getType());
    Assertions.assertFalse(
        Duration.between(dummyTaskDataModels.getTask().getTriggerTime().toInstant(), callTime)
            .isNegative());
  }
}
