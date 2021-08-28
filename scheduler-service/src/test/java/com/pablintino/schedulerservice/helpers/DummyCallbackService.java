package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import lombok.Getter;
import lombok.Setter;
import org.quartz.JobDataMap;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class DummyCallbackService implements ICallbackService {


    public record CallbackCallEntry(SchedulerJobData jobData, JobDataMap jobDataMap, Instant instant){
    }

    @Getter
    private BlockingQueue<CallbackCallEntry> executions = new LinkedBlockingQueue<>();

    @Setter
    private BiConsumer<SchedulerJobData, JobDataMap> callback;

    @Override
    public void executeCallback(SchedulerJobData jobData, JobDataMap jobDataMap) {
        executions.add(new CallbackCallEntry(jobData, jobDataMap, Instant.now()));
        if(callback!=null){
            callback.accept(jobData, jobDataMap);
        }
    }
}
