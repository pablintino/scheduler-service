package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.exceptions.SchedulingException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Service
@RequiredArgsConstructor
public class SchedulingService implements ISchedulingService {

    private static final String TRIGGER_NAME_PREFIX = "cbtrg-";
    private static final String JOB_NAME_PREFIX = "cbjob-";

    private final Scheduler scheduler;
    private final IJobParamsEncoder jobParamsEncoder;

    @Override
    public void scheduleTask(Task task, Endpoint endpoint) {
        try {

            Trigger trigger = prepareNewTrigger(task);
            JobDetail job = prepareNewJob(task, endpoint);
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException ex) {
            throw new SchedulingException("An error occurred when scheduling a job", ex);
        }
    }

    @Override
    public void deleteTask(String taskKey, String taskId) {
        JobKey jobKey = JobKey.jobKey(JOB_NAME_PREFIX + taskId, taskKey);
        try {
            scheduler.deleteJob(jobKey);
        } catch (SchedulerException ex) {
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
            throw new SchedulingException("An exception occurred while retrieving task details.", ex);
        }
        return tasks;
    }

    @Override
    public Task getTask(String key, String taskId) {
        try {
            return getTaskFromJobKey(JobKey.jobKey(JOB_NAME_PREFIX + taskId, key));
        } catch (SchedulerException ex) {
            throw new SchedulingException("An exception occurred while retrieving task details.", ex);
        }
    }


    private Task getTaskFromJobKey(JobKey jobKey) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        if (jobDetail != null) {
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            if (triggers.size() == 1) {
                Trigger trigger = triggers.get(0);
                String cronExpression = trigger instanceof CronTrigger
                        ? ((CronTrigger) trigger).getCronExpression()
                        : null;

                Task task = new Task(
                        jobKey.getName().replaceFirst(JOB_NAME_PREFIX, ""),
                        jobKey.getGroup(),
                        ZonedDateTime.ofInstant(trigger.getStartTime().toInstant(), ZoneOffset.UTC),
                        cronExpression,
                        jobParamsEncoder.removeInternalProperties(jobDetail.getJobDataMap().getWrappedMap())
                );
                return task;
            }
            throw new SchedulingException("Unexpected trigger count found for " + jobKey + " job");
        }
        return null;
    }

    private Trigger prepareNewTrigger(Task task) throws SchedulerException {
        String triggerName = TRIGGER_NAME_PREFIX + task.getId();

        if (task.getTriggerTime().isBefore(ZonedDateTime.now(ZoneOffset.UTC))) {
            throw new SchedulerValidationException("Task time initial time is a past time");
        }

        if (triggerExists(triggerName, task.getKey())) {
            throw new SchedulerValidationException("Task " + task.getId() + " has been already scheduled");
        }

        TriggerBuilder<Trigger> builder = TriggerBuilder
                .newTrigger()
                .withIdentity(triggerName, task.getKey())
                .startAt(Date.from(task.getTriggerTime().toInstant()));

        return StringUtils.isNotBlank(task.getCronExpression()) ?
                prepareCronTrigger(builder, task) :
                builder.withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();

    }

    private Trigger prepareCronTrigger(TriggerBuilder<Trigger> builder, Task task) {
        try {
            new CronExpression(task.getCronExpression());
        } catch (ParseException ex) {
            throw new SchedulerValidationException("The given cron expression is invalid " + task.getCronExpression(), ex);
        }

        return builder.withSchedule(CronScheduleBuilder.cronSchedule(task.getCronExpression())).build();
    }

    private JobDetail prepareNewJob(Task task, Endpoint endpoint) throws SchedulerException {
        String jobName = JOB_NAME_PREFIX + task.getId();

        if (jobExists(jobName, task.getKey())) {
            throw new SchedulingException("Task " + task.getId() + " has been already scheduled");
        }

        if (endpoint.getCallbackType() == CallbackType.HTTP && !UrlValidator.getInstance().isValid(endpoint.getCallbackUrl())) {
            throw new SchedulerValidationException("Endpoint of type HTTP contains an invalid URL");
        }

        return JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(jobName, task.getKey())
                .setJobData(createDataMap(task, endpoint))
                .build();
    }

    private boolean triggerExists(String triggerName, String key) throws SchedulerException {
        return scheduler.getTriggerKeys(
                        GroupMatcher.groupEquals(key))
                .stream().anyMatch(tk -> tk.getName().equals(triggerName));
    }

    private boolean jobExists(String jobName, String key) throws SchedulerException {
        return scheduler.getJobKeys(
                        GroupMatcher.groupEquals(key))
                .stream().anyMatch(tk -> tk.getName().equals(jobName));
    }

    private JobDataMap createDataMap(Task task, Endpoint endpoint) {
        if (task.getTaskData() != null && task.getTaskData().values().stream()
                .anyMatch(o ->
                        !ClassUtils.isPrimitiveOrWrapper(o.getClass()) &&
                                !String.class.equals(o.getClass()))
        ) {
            throw new SchedulerValidationException("Invalid data map type. Only primitives and Strings are supported");
        }

        JobDataMap dataMap = new JobDataMap();
        if (task.getTaskData() != null) {
            dataMap.putAll(task.getTaskData());
        }
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(task, endpoint));
        return dataMap;
    }
}
