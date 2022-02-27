package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.exceptions.SchedulingException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService implements ISchedulingService {

  private static final String TRIGGER_NAME_PREFIX = "cbtrg-";
  private static final String JOB_NAME_PREFIX = "cbjob-";

  private final Scheduler scheduler;
  private final IJobParamsEncoder jobParamsEncoder;

  @Override
  public void scheduleTask(Task task, Endpoint endpoint) throws SchedulerValidationException {
    try {

      Trigger trigger = prepareNewTrigger(task);
      JobDetail job = prepareNewJob(task, endpoint);
      scheduler.scheduleJob(job, trigger);
    } catch (SchedulerException ex) {
      log.error("Error scheduling job for task {} and endpoint {}", task, endpoint, ex);
      throw new SchedulingException("An error occurred when scheduling a job", ex);
    }
  }

  @Override
  public void deleteTask(String taskKey, String taskId) {
    JobKey jobKey = JobKey.jobKey(JOB_NAME_PREFIX + taskId, taskKey);
    try {
      scheduler.deleteJob(jobKey);
    } catch (SchedulerException ex) {
      log.error("Error deleting job for task key {} and id {}", taskKey, taskId, ex);
      throw new SchedulingException("An error occurred while deleting a task " + jobKey, ex);
    }
  }

  @Override
  public List<Task> getTasksForKey(String key) {
    List<Task> tasks = new ArrayList<>();
    try {
      for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.groupEquals(key))) {
        tasks.add(getTaskFromJobKey(jobKey));
      }
    } catch (SchedulerException ex) {
      log.error("Error retrieving tasks for {} key", key, ex);
      throw new SchedulingException("An exception occurred while retrieving task details.", ex);
    }
    return tasks;
  }

  @Override
  public Task getTask(String key, String taskId) {
    return getTaskFromJobKey(JobKey.jobKey(JOB_NAME_PREFIX + taskId, key));
  }

  @Override
  public ScheduleJobMetadata getSchedulerJobMetadata(String key, String taskId) {
    try {
      JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(JOB_NAME_PREFIX + taskId, key));
      return jobDetail != null
          ? jobParamsEncoder.getDecodeSchedulerJobData(jobDetail.getJobDataMap()).getMetadata()
          : null;
    } catch (SchedulerException ex) {
      throw new SchedulingException(
          "An exception occurred while retrieving task stats details.", ex);
    }
  }

  private Task getTaskFromJobKey(JobKey jobKey) {
    try {
      JobDetail jobDetail = scheduler.getJobDetail(jobKey);
      if (jobDetail != null) {
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
        if (triggers.size() == 1) {
          Trigger trigger = triggers.get(0);
          String cronExpression =
              trigger instanceof CronTrigger ? ((CronTrigger) trigger).getCronExpression() : null;

          return new Task(
              jobKey.getName().replaceFirst(JOB_NAME_PREFIX, ""),
              jobKey.getGroup(),
              ZonedDateTime.ofInstant(trigger.getStartTime().toInstant(), ZoneOffset.UTC),
              cronExpression,
              jobParamsEncoder.getDecodeTaskData(jobDetail.getJobDataMap()));
        }
        throw new SchedulingException("Unexpected trigger count found for " + jobKey + " job");
      }
    } catch (SchedulerException ex) {
      /* Internal error that can be changed to a runtime one as is not a common condition */
      log.error("Error retrieving jobdetails/triggers for key {}", jobKey, ex);
      throw new SchedulingException("Cannot retrieve tasks for key " + jobKey, ex);
    }
    return null;
  }

  private Trigger prepareNewTrigger(Task task) throws SchedulerValidationException {
    String triggerName = TRIGGER_NAME_PREFIX + task.getId();

    if (task.getTriggerTime().isBefore(ZonedDateTime.now(ZoneOffset.UTC))) {
      throw new SchedulerValidationException("Task time initial time is a past time");
    }

    if (triggerExists(triggerName, task.getKey())) {
      throw new SchedulerValidationException(
          "Task " + task.getId() + " has been already scheduled");
    }

    TriggerBuilder<Trigger> builder =
        TriggerBuilder.newTrigger()
            .withIdentity(triggerName, task.getKey())
            .startAt(Date.from(task.getTriggerTime().toInstant()));

    return StringUtils.isNotBlank(task.getCronExpression())
        ? prepareCronTrigger(builder, task)
        : builder.withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();
  }

  private Trigger prepareCronTrigger(TriggerBuilder<Trigger> builder, Task task)
      throws SchedulerValidationException {
    try {
      new CronExpression(task.getCronExpression());
    } catch (ParseException ex) {
      throw new SchedulerValidationException(
          "The given cron expression is invalid " + task.getCronExpression(), ex);
    }

    return builder.withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())).build();
  }

  private JobDetail prepareNewJob(Task task, Endpoint endpoint)
      throws SchedulerException, SchedulerValidationException {
    String jobName = JOB_NAME_PREFIX + task.getId();

    if (jobExists(jobName, task.getKey())) {
      throw new SchedulingException("Task " + task.getId() + " has been already scheduled");
    }

    if (endpoint.getCallbackType() == CallbackType.HTTP) {
      try {
        URL url = new URL(endpoint.getCallbackUrl());
        if (Arrays.asList("http", "https").stream().noneMatch(p -> url.getProtocol().equals(p))) {
          throw new SchedulerValidationException("Invalid endpoint protocol");
        }
      } catch (MalformedURLException ex) {
        throw new SchedulerValidationException("Invalid endpoint URL", ex);
      }
    }

    return JobBuilder.newJob(CallbackJob.class)
        .withIdentity(jobName, task.getKey())
        .setJobData(new JobDataMap(jobParamsEncoder.createEncodeJobParameters(task, endpoint)))
        .build();
  }

  private boolean triggerExists(String triggerName, String key) {
    try {
      return scheduler.getTriggerKeys(GroupMatcher.groupEquals(key)).stream()
          .anyMatch(tk -> tk.getName().equals(triggerName));
    } catch (SchedulerException ex) {
      /* Internal error that can be changed to a runtime one as is not a common condition */
      log.error("Error retrieving trigger for key {}", key, ex);
      throw new SchedulingException("Cannot retrieve trigger", ex);
    }
  }

  private boolean jobExists(String jobName, String key) throws SchedulerException {
    return scheduler.getJobKeys(GroupMatcher.groupEquals(key)).stream()
        .anyMatch(tk -> tk.getName().equals(jobName));
  }
}
