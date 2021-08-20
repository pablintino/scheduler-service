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
                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
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
                            jobParamsEncoder.removeJobParameters(jobDetail.getJobDataMap().getWrappedMap())
                    );
                    tasks.add(task);
                }
            }
        } catch (SchedulerException e) {
            // TODO Temporal
            e.printStackTrace();
        }
        return tasks;
    }

    private Trigger prepareNewTrigger(Task task) throws SchedulerException {
        String triggerName = TRIGGER_NAME_PREFIX + task.id();

        if (task.triggerTime().isBefore(ZonedDateTime.now(ZoneOffset.UTC))) {
            throw new SchedulerValidationException("Task time initial time is a past time");
        }

        if (triggerExists(triggerName, task.key())) {
            throw new SchedulerValidationException("Task " + task.id() + " has been already scheduled");
        }

        TriggerBuilder<Trigger> builder = TriggerBuilder
                .newTrigger()
                .withIdentity(triggerName, task.key())
                .startAt(Date.from(task.triggerTime().toInstant()));

        if(StringUtils.isEmpty(task.cronExpression())){
            return builder.withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();
        }
        return builder.withSchedule(CronScheduleBuilder.cronSchedule(task.cronExpression())).build();
    }

    private JobDetail prepareNewJob(Task task, Endpoint endpoint) throws SchedulerException {
        String jobName = JOB_NAME_PREFIX + task.id();

        if (jobExists(jobName, task.key())) {
            throw new SchedulingException("Task " + task.id() + " has been already scheduled");
        }

        if(endpoint.callbackType() == CallbackType.HTTP  && !UrlValidator.getInstance().isValid(endpoint.callbackUrl())){
            throw new SchedulerValidationException("Endpoint of type HTTP contains an invalid URL");
        }

        return JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(jobName, task.key())
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
        if (task.taskData() != null && task.taskData().values().stream()
                .anyMatch(o ->
                        !ClassUtils.isPrimitiveOrWrapper(o.getClass()) &&
                                !String.class.equals(o.getClass()))
        ) {
            throw new SchedulerValidationException("Invalid data map type. Only primitives and Strings are supported");
        }

        JobDataMap dataMap = new JobDataMap();
        if (task.taskData() != null){
            dataMap.putAll(task.taskData());
        }
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(task, endpoint));
        return dataMap;
    }
}
