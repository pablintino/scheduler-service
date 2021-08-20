package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;

import java.util.List;

public interface ISchedulingService {

    void scheduleTask(Task task, Endpoint endpoint);
    void deleteTask(String taskKey, String taskId);
    List<Task> getTasksForKey(String key);

}
