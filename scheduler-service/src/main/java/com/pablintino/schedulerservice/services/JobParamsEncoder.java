package com.pablintino.schedulerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.exceptions.SchedulerServiceException;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobParamsEncoder implements IJobParamsEncoder {

  private static final String SCHEDULER_JOB_DATA_PROPERTY_NAME = "__sch-data";
  private static final String SCHEDULER_TASK_DATA_MAP_NAME = "__sch-task-data";
  private static final TypeReference<HashMap<String, Object>> taskDataTypeReference =
      new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  @Override
  public Map<String, String> createEncodeJobParameters(Task task, Endpoint endpoint) {
    HashMap<String, String> parametersMap = new HashMap<>();

    parametersMap.put(
        SCHEDULER_JOB_DATA_PROPERTY_NAME, createEncodeSchedulerParameters(task, endpoint));
    parametersMap.put(SCHEDULER_TASK_DATA_MAP_NAME, createEncodeTaskParameters(task));

    return parametersMap;
  }

  @Override
  public void updateEncodeSchedulerJobData(
      JobDataMap jobDataMap, SchedulerJobData schedulerJobData) {
    Assert.notNull(schedulerJobData, "schedulerJobData cannot be null");
    Assert.notNull(jobDataMap, "jobDataMap cannot be null");
    if (!jobDataMap.containsKey(SCHEDULER_JOB_DATA_PROPERTY_NAME)) {
      log.warn("Update operation over scheduler job data is a creation one");
    }

    try {
      jobDataMap.put(
          SCHEDULER_JOB_DATA_PROPERTY_NAME, objectMapper.writeValueAsString(schedulerJobData));
    } catch (JsonProcessingException ex) {
      log.error(
          "Cannot serialize SchedulerJobData for task key {} and id {}",
          schedulerJobData.getKey(),
          schedulerJobData.getTaskId(),
          ex);
      throw new SchedulerServiceException("Cannot serialize scheduler internal properties", ex);
    }
  }

  @Override
  public Map<String, Object> getDecodeTaskData(JobDataMap jobDataMap) {
    if (jobDataMap.containsKey(SCHEDULER_TASK_DATA_MAP_NAME)) {
      String serializedTaskData =
          new String(
              Base64.getDecoder().decode(jobDataMap.getString(SCHEDULER_TASK_DATA_MAP_NAME)),
              StandardCharsets.UTF_8);
      try {
        return objectMapper.readValue(serializedTaskData, taskDataTypeReference);
      } catch (JsonProcessingException ex) {
        log.error("Error while deserializing task data map", ex);
      }
    }
    return null;
  }

  @Override
  public SchedulerJobData getDecodeSchedulerJobData(JobDataMap jobDataMap) {
    if (!jobDataMap.containsKey(SCHEDULER_JOB_DATA_PROPERTY_NAME)) {
      throw new SchedulerServiceException(
          "JobDataMap doesn't contain the internal scheduler property");
    }
    try {
      return objectMapper.readValue(
          jobDataMap.getString(SCHEDULER_JOB_DATA_PROPERTY_NAME), SchedulerJobData.class);
    } catch (JsonProcessingException ex) {
      throw new SchedulerServiceException("Cannot deserialize internal json datamap", ex);
    }
  }

  private String createEncodeSchedulerParameters(Task task, Endpoint endpoint) {
    SchedulerJobData jobData =
        new SchedulerJobData(
            task.getId(),
            task.getKey(),
            endpoint.getCallbackUrl(),
            endpoint.getCallbackType(),
            new ScheduleJobMetadata());

    try {
      return objectMapper.writeValueAsString(jobData);
    } catch (JsonProcessingException ex) {
      log.error(
          "Cannot serialize SchedulerJobData for task key {} and id {}",
          task.getKey(),
          task.getId(),
          ex);
      throw new SchedulerServiceException("Cannot serialize scheduler internal properties", ex);
    }
  }

  private String createEncodeTaskParameters(Task task) {

    try {

      return Base64.getEncoder()
          .encodeToString(
              objectMapper
                  .writeValueAsString(
                      task.getTaskData() != null ? task.getTaskData() : Collections.EMPTY_MAP)
                  .getBytes(StandardCharsets.UTF_8));

    } catch (JsonProcessingException ex) {
      log.warn(
          "Cannot serialize Task data map for task key {} and id {}",
          task.getKey(),
          task.getId(),
          ex);
      throw new SchedulerValidationException("Task data map must be JSON serializable", ex);
    }
  }
}
