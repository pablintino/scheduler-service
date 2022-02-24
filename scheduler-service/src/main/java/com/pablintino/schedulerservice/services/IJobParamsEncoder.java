package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.models.Task;
import org.quartz.JobDataMap;

import java.util.Map;

public interface IJobParamsEncoder {

  Map<String, String> createEncodeJobParameters(Task task, Endpoint endpoint)
      throws SchedulerValidationException;

  void updateEncodeSchedulerJobData(JobDataMap jobDataMap, SchedulerJobData schedulerJobData);

  SchedulerJobData getDecodeSchedulerJobData(JobDataMap jobDataMap);

  Object getDecodeTaskData(JobDataMap jobDataMap);
}
