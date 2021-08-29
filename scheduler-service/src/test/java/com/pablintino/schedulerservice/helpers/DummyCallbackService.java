package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.quartz.JobDataMap;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class DummyCallbackService implements ICallbackService {

    @Getter
    @RequiredArgsConstructor
    public class CallbackCallEntry {
        private final SchedulerJobData jobData;
        private final JobDataMap jobDataMap;
        private final ScheduleEventMetadata scheduleEventMetadata;
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
