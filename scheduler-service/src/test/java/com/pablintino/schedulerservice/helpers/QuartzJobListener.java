package com.pablintino.schedulerservice.helpers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class QuartzJobListener implements JobListener {

    @Getter
    @RequiredArgsConstructor
    public class JobExecutionEntry {
        private final JobExecutionContext jobExecutionContext;
        private final JobExecutionException ex;
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

    public List<JobExecutionEntry> waitJobExecutions(int count, long millis){
        long remaining = millis;
        List<JobExecutionEntry> entries = new ArrayList<>();
        try {
            do{
                Instant start = Instant.now();
                JobExecutionEntry jobExecutionEntry = executions.poll(remaining, TimeUnit.MILLISECONDS);
                if(jobExecutionEntry!=null){
                    entries.add(jobExecutionEntry);
                }
                remaining -= Instant.now().toEpochMilli() - start.toEpochMilli();
            }while (remaining>0 && entries.size() < count);
        } catch (InterruptedException e) {
            return Assertions.fail("Exception while waiting job execution");
        }
        return entries;
    }
}
