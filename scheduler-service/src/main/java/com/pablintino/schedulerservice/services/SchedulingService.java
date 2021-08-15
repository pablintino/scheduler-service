package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationError;
import com.pablintino.schedulerservice.exceptions.SchedulingException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ClassUtils;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import org.quartz.SimpleScheduleBuilder;

import java.time.ZoneId;
import java.util.Date;


@Service
@RequiredArgsConstructor
public class SchedulingService implements ISchedulingService {

    private static final String TRIGGER_NAME_PREFIX="cbtrg-";
    private static final String JOB_NAME_PREFIX="cbjob-";

    private final Scheduler scheduler;
    private final IJobParamsEncoder jobParamsEncoder;

    @Override
    public void scheduleTask(Task task, Endpoint endpoint) throws SchedulingException {
        try {

            Trigger trigger = prepareNewTrigger(task);
            JobDetail job = prepareNewJob(task, endpoint);
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException | SchedulerValidationError ex) {
            throw new SchedulingException("An error occurred when scheduling a job", ex);
        }
    }

    private Trigger prepareNewTrigger(Task task) throws SchedulerException, SchedulingException {
        String triggerName = TRIGGER_NAME_PREFIX + task.id();

        if (triggerExists(triggerName, task.key())) {
            throw new SchedulingException("Task " + task.id() + " has been already scheduled");
        }

        return TriggerBuilder
                .newTrigger()
                .withIdentity(triggerName, task.key())
                .startAt(Date.from(task.triggerTime().atZone(ZoneId.systemDefault()).toInstant()))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule())
                .build();
    }

    private JobDetail prepareNewJob(Task task, Endpoint endpoint) throws SchedulerException, SchedulingException {
        String jobName = JOB_NAME_PREFIX + task.id();

        if (jobExists(jobName, task.key())) {
            throw new SchedulingException("Task " + task.id() + " has been already scheduled");
        }

        return JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(jobName, task.key())
                .setJobData(createDataMap(task,endpoint))
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

    private JobDataMap createDataMap(Task task, Endpoint endpoint) throws SchedulerValidationError {
        if (task.taskData().values().stream()
                .anyMatch(o ->
                        !ClassUtils.isPrimitiveOrWrapper(o.getClass()) &&
                                !String.class.equals(o.getClass()))
        ) {
            // TODO Replace by a proper exception
            throw new RuntimeException("Invalid data map type. Only primitives and String supported");
        }

        JobDataMap dataMap = new JobDataMap();
        dataMap.putAll(task.taskData());
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(task, endpoint));
        return dataMap;
    }
}
