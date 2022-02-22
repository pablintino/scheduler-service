package com.pablintino.schedulerservice.helpers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

@RequiredArgsConstructor
public class DummyCallbackService implements ICallbackService {

  private final ObjectMapper objectMapper;
  private static final TypeReference<HashMap<String, Object>> taskDataTypeReference =
      new TypeReference<>() {};

  @Getter
  @RequiredArgsConstructor
  public static class CallbackCallEntry {
    private final SchedulerJobData jobData;
    private final Map<String, Object> taskDataMap;
  }

  @Getter private BlockingQueue<CallbackCallEntry> executions = new LinkedBlockingQueue<>();

  @Setter private BiConsumer<SchedulerJobData, Map<String, Object>> callback;

  @Override
  @SneakyThrows
  public void executeCallback(SchedulerJobData jobData, Map<String, Object> taskDataMap) {
    /* Simple deep copy by json serialization */
    SchedulerJobData clonedSchedulerJobData =
        objectMapper.readValue(objectMapper.writeValueAsString(jobData), SchedulerJobData.class);
    Map<String, Object> clonedTaskDataMap =
        objectMapper.readValue(objectMapper.writeValueAsString(taskDataMap), taskDataTypeReference);
    executions.add(new CallbackCallEntry(clonedSchedulerJobData, clonedTaskDataMap));
    if (callback != null) {
      callback.accept(clonedSchedulerJobData, clonedTaskDataMap);
    }
  }
}
