package com.pablintino.schedulerservice.quartz;

import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.ICallbackService;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.Date;

@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CallbackJob implements Job {


    private static final String ATTEMPT_COUNTER_KEY = "__failure_attempts";

    private ICallbackService callbackService;
    private IJobParamsEncoder jobParamsEncoder;
    private IReeschedulableAnnotationResolver reeschedulableAnnotationResolver;
    private Integer retrialAttempts;
    private Long retrialDelay;


    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Job " + context.getJobDetail() + " starts its execution");
        try {
            JobDataMap jobDataMap = context.getMergedJobDataMap();
            SchedulerJobData jobData = jobParamsEncoder.extractDecodeJobParameters(jobDataMap);
            callbackService.executeCallback(jobData, jobDataMap);
        } catch (Exception ex) {
            manageFailure(ex, context);
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

    private void manageFailure(Exception ex, JobExecutionContext jobExecutionContext) throws JobExecutionException {
        if (reeschedulableAnnotationResolver.getAnnotatedTypes().stream().anyMatch(exType -> exType.isAssignableFrom(ex.getClass()))) {
            try {
                JobDataMap jobDataMap = jobExecutionContext.getMergedJobDataMap();
                Integer attempts = retrialAttempts;
                if (jobDataMap.containsKey(ATTEMPT_COUNTER_KEY)) {
                    attempts = jobDataMap.getIntegerFromString(ATTEMPT_COUNTER_KEY);
                } else {
                    jobDataMap.putAsString(ATTEMPT_COUNTER_KEY, attempts);
                }

                Trigger trigger = TriggerBuilder
                        .newTrigger()
                        .withIdentity(
                                jobExecutionContext.getTrigger().getKey().getName() + "_retry_" + attempts,
                                jobExecutionContext.getTrigger().getKey().getGroup()
                        )
                        .startAt(Date.from(Instant.now().plusMillis(retrialDelay)))
                        .withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();

                jobExecutionContext.getScheduler().scheduleJob(jobExecutionContext.getJobDetail(), trigger);

            } catch (Exception intEx) {
                log.error("Error managing job failure. Discarding job " + jobExecutionContext.getJobDetail().getKey());
            }
        } else {
            log.error("Non recoverable exception during job execution. Discarding job " + jobExecutionContext.getJobDetail().getKey());
        }
        JobExecutionException jobExecutionException = new JobExecutionException(ex);
        // This is important. The job should be unscheduled and never refire!
        jobExecutionException.setRefireImmediately(false);
        jobExecutionException.setUnscheduleAllTriggers(true);
        throw jobExecutionException;
    }
}
