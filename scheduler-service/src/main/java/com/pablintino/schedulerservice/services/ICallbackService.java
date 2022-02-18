package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.models.SchedulerJobData;

import java.util.Map;

public interface ICallbackService {

  void executeCallback(SchedulerJobData jobData, Map<String, Object> taskDataMap);
}
