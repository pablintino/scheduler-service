package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulingException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;

public interface ISchedulingService {

    void scheduleTask(Task task, Endpoint endpoint) throws SchedulingException;

}
