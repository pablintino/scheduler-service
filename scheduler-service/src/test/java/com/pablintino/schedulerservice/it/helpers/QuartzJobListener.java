package com.pablintino.schedulerservice.it.helpers;

import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class QuartzJobListener implements JobListener {

    public record JobExecutionEntry(JobExecutionContext jobExecutionContext, JobExecutionException ex){
    }

    @Getter
    private BlockingQueue<JobExecutionEntry> executions = new LinkedBlockingQueue<>();

    @Override
    public String getName() {
        return "it-test-job-listener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {
        //NOOP
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {
        //NOOP
    }

    @Override
    public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException ex) {
        executions.add(new JobExecutionEntry(jobExecutionContext, ex));
    }

    public JobExecutionEntry waitJobExecution(long millis){
        try {
            return executions.poll(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return Assertions.fail("Exception while waiting job execution");
        }
    }
}