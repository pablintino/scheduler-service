package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
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


    public record CallbackCallEntry(SchedulerJobData jobData, JobDataMap jobDataMap, ScheduleEventMetadata scheduleEventMetadata){
    }

    @Getter
    private BlockingQueue<CallbackCallEntry> executions = new LinkedBlockingQueue<>();

    @Setter
    private BiConsumer<SchedulerJobData, JobDataMap> callback;

    @Override
    public void executeCallback(SchedulerJobData jobData, JobDataMap jobDataMap, ScheduleEventMetadata scheduleEventMetadata) {
        executions.add(new CallbackCallEntry(jobData, jobDataMap, scheduleEventMetadata));
        if(callback!=null){
            callback.accept(jobData, jobDataMap);
        }
    }
}
