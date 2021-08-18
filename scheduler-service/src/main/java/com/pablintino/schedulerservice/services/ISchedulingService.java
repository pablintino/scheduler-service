package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.exceptions.SchedulingException;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;

import java.util.List;

public interface ISchedulingService {

    void scheduleTask(Task task, Endpoint endpoint) throws SchedulingException;
    List<Task> getTasksForKey(String key);

}
