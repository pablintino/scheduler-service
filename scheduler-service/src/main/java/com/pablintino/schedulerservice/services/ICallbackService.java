package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.models.SchedulerJobData;

public interface ICallbackService {

  void executeCallback(SchedulerJobData jobData, Object taskData);
}
