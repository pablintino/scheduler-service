package com.pablintino.schedulerservice.quartz;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.ICallbackService;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class CallbackJob implements Job {

  private ICallbackService callbackService;
  private IJobParamsEncoder jobParamsEncoder;
  private IReeschedulableAnnotationResolver reeschedulableAnnotationResolver;
  private long retrialAttempts;
  private Long retrialDelay;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    log.debug("Job " + context.getJobDetail() + " starts its execution");
    SchedulerJobData schedulerJobData = null;
    Instant handleStartInstant = Instant.now();
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();
    try {
      schedulerJobData = jobParamsEncoder.getDecodeSchedulerJobData(jobDataMap);

      preUpdateExecutionMetadata(schedulerJobData.getMetadata(), context);

      callbackService.executeCallback(
          schedulerJobData, jobParamsEncoder.getDecodeTaskData(jobDataMap));

      /* Succeed, reset notification attempts */
      resetNotificationAttempts(schedulerJobData.getMetadata());

    } catch (SchedulerValidationException schedulerValidationException) {
      log.error(
          "Unable to decode job parameters. Canceling job " + context.getJobDetail().getKey(),
          schedulerValidationException);
      throw new JobExecutionException(schedulerValidationException, false);
    } catch (Exception ex) {
      manageFailure(ex, context, schedulerJobData);
    } finally {
      if (schedulerJobData != null) {
        jobParamsEncoder.updateEncodeSchedulerJobData(jobDataMap, schedulerJobData);
      }
    }
    log.debug(
        "Job {} finished its execution in {} ms",
        context.getJobDetail(),
        Duration.between(handleStartInstant, Instant.now()).toMillis());
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
  public void setReeschedulableAnnotationResolver(
      IReeschedulableAnnotationResolver reeschedulableAnnotationResolver) {
    this.reeschedulableAnnotationResolver = reeschedulableAnnotationResolver;
  }

  @Value("${com.pablintino.scheduler.failure-attempts:5}")
  public void setFailureRetrialAttempts(Long retrialAttempts) {
    this.retrialAttempts = retrialAttempts;
  }

  @Value("${com.pablintino.scheduler.failure-attempt-delay:5000}")
  public void setFailureDelayAttempts(Long retrialDelay) {
    this.retrialDelay = retrialDelay;
  }

  private static void preUpdateExecutionMetadata(
      ScheduleJobMetadata scheduleJobMetadata, JobExecutionContext context) {
    scheduleJobMetadata.setTriggerTime(context.getFireTime().toInstant());
    scheduleJobMetadata.setExecutions(scheduleJobMetadata.getExecutions() + 1);
  }

  private static void resetNotificationAttempts(ScheduleJobMetadata scheduleJobMetadata) {
    scheduleJobMetadata.setNotificationAttempt(0);
  }

  private void manageFailure(
      Exception ex, JobExecutionContext jobExecutionContext, SchedulerJobData schedulerJobData)
      throws JobExecutionException {
    boolean rescheduled = false;
    if (reeschedulableAnnotationResolver.getAnnotatedTypes().stream()
        .anyMatch(exType -> exType.isAssignableFrom(ex.getClass()))) {
      try {
        long attemptNumber = getIncrementJobAttempt(schedulerJobData.getMetadata());
        if (attemptNumber <= retrialAttempts) {
          log.debug(
              "Callback job "
                  + jobExecutionContext.getJobDetail().getKey()
                  + " reschedule attempt "
                  + attemptNumber);

          Trigger trigger = rebuildTrigger(jobExecutionContext);
          jobExecutionContext
              .getScheduler()
              .rescheduleJob(jobExecutionContext.getTrigger().getKey(), trigger);
          rescheduled = true;
        } else {
          log.warn(
              "Exception in job with already consumed reattempts. Discarding job "
                  + jobExecutionContext.getJobDetail().getKey());
        }
      } catch (Exception intEx) {
        log.error(
            "Error managing job failure. Discarding job "
                + jobExecutionContext.getJobDetail().getKey());
      }
    } else {
      log.error(
          "Non recoverable exception during job execution. Discarding job "
              + jobExecutionContext.getJobDetail().getKey());
    }

    /* Increment failure count and set last failure instant */
    schedulerJobData.getMetadata().setFailures(schedulerJobData.getMetadata().getFailures() + 1);
    schedulerJobData
        .getMetadata()
        .setLastFailureTime(jobExecutionContext.getFireTime().toInstant());

    throw !rescheduled
        ? deleteFiringTrigger(jobExecutionContext, ex)
        : new JobExecutionException(ex, false);
  }

  private static JobExecutionException deleteFiringTrigger(
      JobExecutionContext jobExecutionContext, Exception causeException) {
    try {
      jobExecutionContext.getScheduler().unscheduleJob(jobExecutionContext.getTrigger().getKey());
    } catch (SchedulerException ex) {
      log.error("Error deleting failed trigger", ex);
    }
    return new JobExecutionException(causeException, false);
  }

  private long getIncrementJobAttempt(ScheduleJobMetadata scheduleEventMetadata)
      throws JobExecutionException {
    if (scheduleEventMetadata != null && scheduleEventMetadata.getNotificationAttempt() >= 0) {
      long attempts = scheduleEventMetadata.getNotificationAttempt() + 1;
      scheduleEventMetadata.setNotificationAttempt(attempts);
      return attempts;
    }
    throw new JobExecutionException(false);
  }

  private Trigger rebuildTrigger(JobExecutionContext jobExecutionContext) {
    ScheduleBuilder scheduleBuilder =
        jobExecutionContext.getTrigger() instanceof CronTrigger
            ? CronScheduleBuilder.cronSchedule(
                ((CronTrigger) jobExecutionContext.getTrigger()).getCronExpression())
            : SimpleScheduleBuilder.simpleSchedule();

    return TriggerBuilder.newTrigger()
        .withIdentity(jobExecutionContext.getTrigger().getKey())
        .startAt(Date.from(Instant.now().plusMillis(retrialDelay)))
        .withSchedule(scheduleBuilder)
        .build();
  }
}
