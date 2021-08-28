package com.pablintino.schedulerservice.quartz;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.ICallbackService;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CallbackJob implements Job {

    private ICallbackService callbackService;
    private IJobParamsEncoder jobParamsEncoder;
    private IReeschedulableAnnotationResolver reeschedulableAnnotationResolver;
    private int retrialAttempts;
    private Long retrialDelay;


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Job " + context.getJobDetail() + " starts its execution");
        ScheduleEventMetadata scheduleEventMetadata = null;
        try {
            JobDataMap copyJobDataMap = new JobDataMap(context.getJobDetail().getJobDataMap());
            scheduleEventMetadata = jobParamsEncoder.extractDecodeSchedulerEventMetadata(copyJobDataMap);
            SchedulerJobData jobData = jobParamsEncoder.extractDecodeJobParameters(copyJobDataMap);
            scheduleEventMetadata.setTriggerTime(ZonedDateTime.now(ZoneOffset.UTC).toInstant());
            callbackService.executeCallback(jobData, copyJobDataMap, scheduleEventMetadata);
        } catch (SchedulerValidationException schedulerValidationException) {
            log.error("Unable to decode job parameters. Canceling job " + context.getJobDetail().getKey(), schedulerValidationException);
            throw createUnrecoverableException(schedulerValidationException);
        } catch (Exception ex) {
            manageFailure(ex, context, scheduleEventMetadata);
        } finally {
            if (scheduleEventMetadata != null) {
                jobParamsEncoder.encodeUpdateSchedulerEventMetadata(context.getJobDetail().getJobDataMap(), scheduleEventMetadata);
            }
        }
        log.debug("Job " + context.getJobDetail() + " finished its execution");
    }

    @Autowired
    public void setJobParamsEncoder(IJobParamsEncoder jobParamsEncoder) {
        this.jobParamsEncoder = jobParamsEncoder;
    }

    @Autowired
    public void setCallbackService(ICallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Autowired
    public void setReeschedulableAnnotationResolver(IReeschedulableAnnotationResolver reeschedulableAnnotationResolver) {
        this.reeschedulableAnnotationResolver = reeschedulableAnnotationResolver;
    }

    @Value("${com.pablintino.scheduler.failure-attempts:5}")
    public void setFailureRetrialAttempts(Integer retrialAttempts) {
        this.retrialAttempts = retrialAttempts;
    }

    @Value("${com.pablintino.scheduler.failure-attempt-delay:5000}")
    public void setFailureDelayAttempts(Long retrialDelay) {
        this.retrialDelay = retrialDelay;
    }

    private void manageFailure(Exception ex, JobExecutionContext jobExecutionContext, ScheduleEventMetadata scheduleEventMetadata) throws JobExecutionException {
        if (reeschedulableAnnotationResolver.getAnnotatedTypes().stream().anyMatch(exType -> exType.isAssignableFrom(ex.getClass()))) {
            try {
                int attemptNumber = getIncrementJobAttempt(scheduleEventMetadata);
                if (attemptNumber <= retrialAttempts) {
                    Trigger trigger = TriggerBuilder
                            .newTrigger()
                            .withIdentity(
                                    getRetryTriggerName(jobExecutionContext.getTrigger().getKey().getName(), attemptNumber),
                                    jobExecutionContext.getTrigger().getKey().getGroup()
                            )
                            .startAt(Date.from(Instant.now().plusMillis(retrialDelay)))
                            .withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();

                    log.debug("Callback job " + jobExecutionContext.getJobDetail().getKey() + " reschedule attempt " + attemptNumber);

                    jobExecutionContext.getScheduler().rescheduleJob(jobExecutionContext.getTrigger().getKey(), trigger);
                    throw new JobExecutionException(ex, false);
                } else {
                    log.error("Exception in job with already consumed reattempts. Discarding job " + jobExecutionContext.getJobDetail().getKey());
                }
            } catch (Exception intEx) {
                log.error("Error managing job failure. Discarding job " + jobExecutionContext.getJobDetail().getKey());
            }
        } else {
            log.error("Non recoverable exception during job execution. Discarding job " + jobExecutionContext.getJobDetail().getKey());
        }
        throw createUnrecoverableException(ex);
    }

    private int getIncrementJobAttempt(ScheduleEventMetadata scheduleEventMetadata) throws JobExecutionException {
        if (scheduleEventMetadata != null && scheduleEventMetadata.getAttempt() >= 0) {
            int attempts = scheduleEventMetadata.getAttempt() + 1;
            scheduleEventMetadata.setAttempt(attempts);
            return attempts;
        }
        throw createUnrecoverableException(null);
    }

    private static String getRetryTriggerName(String currentName, int attempt) {
        int p = currentName.lastIndexOf("_retry");
        if (p >= 0) {
            return currentName.substring(0, p) + "_retry_" + attempt;
        }
        return currentName + "_retry_" + attempt;

    }

    private static JobExecutionException createUnrecoverableException(Exception ex) {
        JobExecutionException jobExecutionException = new JobExecutionException(ex, false);
        jobExecutionException.setUnscheduleAllTriggers(true);
        return jobExecutionException;
    }
}
