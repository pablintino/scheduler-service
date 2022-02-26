package com.pablintino.schedulerservice.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
public class DummyCallbackService implements ICallbackService {

  private final ObjectMapper objectMapper;

  @Getter
  @RequiredArgsConstructor
  public static class CallbackCallEntry {
    private final SchedulerJobData jobData;
    private final Object taskData;
  }

  @Getter private BlockingQueue<CallbackCallEntry> executions = new LinkedBlockingQueue<>();

  @Setter private BiConsumer<SchedulerJobData, Object> callback;

  @Override
  @SneakyThrows
  public void executeCallback(SchedulerJobData jobData, Object taskData) {
    /* Simple deep copy by json serialization */
    SchedulerJobData clonedSchedulerJobData =
        objectMapper.readValue(objectMapper.writeValueAsString(jobData), SchedulerJobData.class);
    Object clonedTaskData =
        objectMapper.readValue(objectMapper.writeValueAsString(taskData), Object.class);
    executions.add(new CallbackCallEntry(clonedSchedulerJobData, clonedTaskData));
    if (callback != null) {
      callback.accept(clonedSchedulerJobData, clonedTaskData);
    }
  }
}
