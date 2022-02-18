package com.pablintino.schedulerservice.helpers;

import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class DummyCallbackService implements ICallbackService {

  @Getter
  @RequiredArgsConstructor
  public static class CallbackCallEntry {
    private final SchedulerJobData jobData;
    private final Map<String, Object> taskDataMap;
  }

  @Getter private BlockingQueue<CallbackCallEntry> executions = new LinkedBlockingQueue<>();

  @Setter private BiConsumer<SchedulerJobData, Map<String, Object>> callback;

  @Override
  public void executeCallback(SchedulerJobData jobData, Map<String, Object> taskDataMap) {
    executions.add(new CallbackCallEntry(jobData, taskDataMap));
    if (callback != null) {
      callback.accept(jobData, taskDataMap);
    }
  }
}
