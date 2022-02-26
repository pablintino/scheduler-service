package com.pablintino.schedulerservice.helpers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.dtos.CallbackDescriptorDto;
import com.pablintino.schedulerservice.dtos.CallbackMethodTypeDto;
import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.quartz.*;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DummyTasksProvider {

  private final IJobParamsEncoder jobParamsEncoder;
  private final ObjectMapper objectMapper;

  public DummyTaskData createSimpleJobData() {
    DummyTaskData taskData = new DummyTaskData();
    Random random = new Random();
    taskData.setTestDate(Date.from(Instant.now()));
    taskData.setTestFloat(random.nextFloat());
    taskData.setTestInt(random.nextInt());
    taskData.setTestList(Arrays.asList("test-1", "test-2", "test-3"));
    Map<String, Integer> testMap = new HashMap<>();
    testMap.put("test-1", random.nextInt());
    testMap.put("test-2", random.nextInt());
    testMap.put("test-3", random.nextInt());
    taskData.setTestMap(testMap);
    return taskData;
  }

  public void validateDummyTaskData(Object expectedTaskData, Object dummyTaskData) {
    Assertions.assertNotNull(expectedTaskData);
    Assertions.assertNotNull(dummyTaskData);
    try {
      Assertions.assertEquals(
          objectMapper.readValue(
              objectMapper.writeValueAsString(expectedTaskData), DummyTaskData.class),
          objectMapper.readValue(
              objectMapper.writeValueAsString(dummyTaskData), DummyTaskData.class));
    } catch (JsonProcessingException e) {
      Assertions.fail("Cannot serialize to compare dummy task data");
    }
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
            /* This prefix follows the one used in the scheduling service. Don't want to maka that constant public */
            .withIdentity("cbtrg-" + name, "it-test")
            .startAt(startDate)
            .withSchedule(triggerBuilder)
            .build();
    Task dummyTask =
        new Task(
            name,
            "it-test",
            ZonedDateTime.ofInstant(startDate.toInstant(), ZoneOffset.UTC),
            cron,
            createSimpleJobData());

    Endpoint dummyEndpoint = new Endpoint(CallbackType.AMQP, null);

    JobDataMap dataMap = null;
    try {
      dataMap =
          new JobDataMap(jobParamsEncoder.createEncodeJobParameters(dummyTask, dummyEndpoint));
    } catch (SchedulerValidationException e) {
      Assertions.fail("Dummy payload cannot be serialized");
    }

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
      List<DummyCallbackService.CallbackCallEntry> callbackEntries,
      List<QuartzJobListener.JobExecutionEntry> executions,
      int retries,
      long attemptsDelay) {
    Assertions.assertEquals(retries + 1, callbackEntries.size());
    Assertions.assertEquals(executions.size(), callbackEntries.size());

    for (int index = 0; index < callbackEntries.size(); index++) {
      DummyCallbackService.CallbackCallEntry currentEntry = callbackEntries.get(index);
      QuartzJobListener.JobExecutionEntry currentExecution = executions.get(index);
      validateCommonSchedulerDataParams(dummyTaskDataModels, currentEntry.getJobData());

      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getExecutions());

      /* First execution should contain zero failures. Then this field should increment counter-like */
      Assertions.assertEquals(index, currentEntry.getJobData().getMetadata().getFailures());

      /* First execution is attempt 0. Then this field should increment counter-like */
      Assertions.assertEquals(
          index, currentEntry.getJobData().getMetadata().getNotificationAttempt());

      /* Ensure task data has not changed (User data given as task data should be equal to the one provided on creation */
      validateDummyTaskData(
          dummyTaskDataModels.getTask().getTaskData(), currentEntry.getTaskData());

      /* Ensure reported trigger time is the same as the real one */
      Assertions.assertEquals(
          currentEntry.getJobData().getMetadata().getTriggerTime(),
          currentExecution.getJobExecutionContext().getFireTime().toInstant());

      if (index == 0) {
        /* Check that execution is in time */
        Assertions.assertEquals(
            currentExecution
                .getJobExecutionContext()
                .getFireTime()
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS),
            dummyTaskDataModels
                .getTrigger()
                .getNextFireTime()
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS));
      } else {
        /* Reattempts (index != 0) should contain the timestamp of the last failure */
        Assertions.assertNotNull(currentEntry.getJobData().getMetadata().getLastFailureTime());

        DummyCallbackService.CallbackCallEntry previousEntry = callbackEntries.get(index - 1);
        validateRetriesTime(
            currentEntry, previousEntry, attemptsDelay, dummyTaskDataModels.getTrigger());
      }
    }
  }

  public void validateCronReattemptedAndRecoveredJob(
      DummyTaskDataModels dummyTaskDataModels,
      List<DummyCallbackService.CallbackCallEntry> callbackEntries,
      List<QuartzJobListener.JobExecutionEntry> executions,
      long attemptsDelay,
      int recoverRetry) {
    Assertions.assertTrue(recoverRetry + 1 < callbackEntries.size());
    Assertions.assertEquals(callbackEntries.size(), executions.size());

    for (int index = 0; index < callbackEntries.size(); index++) {
      DummyCallbackService.CallbackCallEntry currentEntry = callbackEntries.get(index);
      QuartzJobListener.JobExecutionEntry currentExecution = executions.get(index);
      validateCommonSchedulerDataParams(dummyTaskDataModels, currentEntry.getJobData());

      Assertions.assertEquals(index + 1, currentEntry.getJobData().getMetadata().getExecutions());

      /* Ensure task data has not changed */
      validateDummyTaskData(
          dummyTaskDataModels.getTask().getTaskData(), currentEntry.getTaskData());

      /* Ensure reported trigger time is the same as the real one */
      Assertions.assertEquals(
          currentEntry.getJobData().getMetadata().getTriggerTime(),
          currentExecution.getJobExecutionContext().getFireTime().toInstant());

      /* Reattempts (index != 0) should contain the timestamp of the last failure */
      if (index == 0) {
        // First failed (original) execution
        Assertions.assertNull(currentEntry.getJobData().getMetadata().getLastFailureTime());
        Assertions.assertEquals(0L, currentEntry.getJobData().getMetadata().getFailures());
        Assertions.assertEquals(
            0, currentEntry.getJobData().getMetadata().getNotificationAttempt());

        /* Check that execution is in time */
        Assertions.assertEquals(
            currentExecution
                .getJobExecutionContext()
                .getFireTime()
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS),
            dummyTaskDataModels
                .getTrigger()
                .getNextFireTime()
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS));
      } else if (index < recoverRetry) {
        // Retries
        /* Ensure last failure time contains the timestamp of the previous triggered run */
        Assertions.assertEquals(
            executions.get(index - 1).getJobExecutionContext().getFireTime().toInstant(),
            currentEntry.getJobData().getMetadata().getLastFailureTime());

        Assertions.assertEquals(index, currentEntry.getJobData().getMetadata().getFailures());

        /* Reattempts (index != 0) should contain the timestamp of the last failure. Despite recover succeed */
        Assertions.assertNotNull(currentEntry.getJobData().getMetadata().getLastFailureTime());
        validateRetriesTime(
            currentEntry,
            callbackEntries.get(index - 1),
            attemptsDelay,
            dummyTaskDataModels.getTrigger());
      } else {
        // Normal execution after failure
        /* Ensure last failure time contains the timestamp of the previous failed run */
        Assertions.assertEquals(
            executions.get(recoverRetry - 1).getJobExecutionContext().getFireTime().toInstant(),
            currentEntry.getJobData().getMetadata().getLastFailureTime());

        Assertions.assertEquals(
            recoverRetry, currentEntry.getJobData().getMetadata().getFailures());

        /* Reattempts (index != 0) should contain the timestamp of the last failure. Despite recover succeed */
        Assertions.assertNotNull(currentEntry.getJobData().getMetadata().getLastFailureTime());

        /* After recover the first callback should contain the notification attempt number. After that attempt the counter is reset */
        Assertions.assertEquals(
            recoverRetry == index ? recoverRetry : 0,
            currentEntry.getJobData().getMetadata().getNotificationAttempt());
      }
    }
  }

  public void validateSimpleValidJob(
      DummyTaskDataModels dummyTaskDataModels,
      DummyCallbackService.CallbackCallEntry callbackCallEntry,
      QuartzJobListener.JobExecutionEntry jobExecution) {
    validateCommonSchedulerDataParams(dummyTaskDataModels, callbackCallEntry.getJobData());

    /* Ensure that the task has been executed in time (50ms toleration) */
    Assertions.assertEquals(
        jobExecution
            .getJobExecutionContext()
            .getFireTime()
            .toInstant()
            .truncatedTo(ChronoUnit.SECONDS),
        dummyTaskDataModels.getTask().getTriggerTime().toInstant().truncatedTo(ChronoUnit.SECONDS));

    /* Ensure reported trigger time is the same as the real one */
    Assertions.assertEquals(
        callbackCallEntry.getJobData().getMetadata().getTriggerTime(),
        jobExecution.getJobExecutionContext().getFireTime().toInstant());

    /* Ensure task data is preserved without changes */
    validateDummyTaskData(
        dummyTaskDataModels.getTask().getTaskData(), callbackCallEntry.getTaskData());
  }

  public void validateCronValidJob(
      DummyTaskDataModels dummyTaskDataModels,
      List<DummyCallbackService.CallbackCallEntry> callbackEntries,
      List<QuartzJobListener.JobExecutionEntry> executions) {

    Assertions.assertFalse(executions.isEmpty());
    Assertions.assertEquals(callbackEntries.size(), executions.size());

    for (int index = 0; index < executions.size(); index++) {
      QuartzJobListener.JobExecutionEntry execution = executions.get(index);
      DummyCallbackService.CallbackCallEntry entry = callbackEntries.get(index);

      SchedulerJobData jobData = entry.getJobData();

      /* Assert common simple stuff */
      validateCommonSchedulerDataParams(dummyTaskDataModels, jobData);

      /* Ensure that the task has been executed in time (within a second) */
      if (index == 0) {
        /* Simple truncation doesn't work for a cron first run. That's why here the difference between both instants is used */
        Assertions.assertTrue(
            Math.abs(
                    Duration.between(
                            execution.getJobExecutionContext().getFireTime().toInstant(),
                            dummyTaskDataModels.getTask().getTriggerTime().toInstant())
                        .toMillis())
                < 1000);
      } else {
        CronExpression expression = null;
        try {
          expression = new CronExpression(dummyTaskDataModels.getTask().getCronExpression());
        } catch (ParseException e) {
          Assertions.fail("Cron expression is not valid");
        }
        Assertions.assertEquals(
            expression
                .getNextValidTimeAfter(
                    executions.get(index - 1).getJobExecutionContext().getFireTime())
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS),
            execution
                .getJobExecutionContext()
                .getFireTime()
                .toInstant()
                .truncatedTo(ChronoUnit.SECONDS));
      }

      /* Ensure reported trigger time is the same as the real one */
      Assertions.assertEquals(
          jobData.getMetadata().getTriggerTime(),
          execution.getJobExecutionContext().getFireTime().toInstant());

      /* Ensure task data is preserved without changes */
      validateDummyTaskData(dummyTaskDataModels.getTask().getTaskData(), entry.getTaskData());
    }
  }

  private static void validateCommonSchedulerDataParams(
      DummyTaskDataModels dummyTaskDataModels, SchedulerJobData jobData) {
    Assertions.assertEquals(dummyTaskDataModels.getTask().getId(), jobData.getTaskId());
    Assertions.assertEquals(dummyTaskDataModels.getTask().getKey(), jobData.getKey());
    Assertions.assertEquals(
        dummyTaskDataModels.getEndpoint().getCallbackUrl(), jobData.getCallbackUrl());
    Assertions.assertEquals(dummyTaskDataModels.getEndpoint().getCallbackType(), jobData.getType());
    Assertions.assertNotNull(jobData.getMetadata());
    Assertions.assertNotNull(jobData.getMetadata().getTriggerTime());
  }

  @SneakyThrows
  private static void validateRetriesTime(
      DummyCallbackService.CallbackCallEntry currentEntry,
      DummyCallbackService.CallbackCallEntry previousEntry,
      long attemptsDelay,
      Trigger trigger) {

    Instant expectedRetriggerTime = null;
    if (trigger instanceof CronTrigger) {
      Date previousDatePlusRetries =
          Date.from(
              previousEntry
                  .getJobData()
                  .getMetadata()
                  .getTriggerTime()
                  .plus(attemptsDelay, ChronoUnit.MILLIS));
      expectedRetriggerTime =
          new CronExpression(((CronTrigger) trigger).getCronExpression())
              .getNextValidTimeAfter(previousDatePlusRetries)
              .toInstant();

    } else if (trigger instanceof SimpleTrigger) {
      expectedRetriggerTime =
          previousEntry
              .getJobData()
              .getMetadata()
              .getTriggerTime()
              .plus(attemptsDelay, ChronoUnit.MILLIS);
    } else {
      Assertions.fail("Not supported trigger type");
    }

    Assertions.assertEquals(
        currentEntry.getJobData().getMetadata().getTriggerTime().truncatedTo(ChronoUnit.SECONDS),
        expectedRetriggerTime.truncatedTo(ChronoUnit.SECONDS));
  }
}
