package com.pablintino.schedulerservice.services.mappers;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.dtos.TaskStatsDto;
import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.Task;
import org.springframework.stereotype.Component;

@Component
public class SchedulingDtoMapper implements ISchedulingDtoMapper {

  @Override
  public Task mapTaskFromDto(ScheduleRequestDto scheduleRequestDto) {
    if (scheduleRequestDto == null) {
      throw new SchedulerValidationException("scheduleRequestDto cannot be null");
    }

    return new Task(
        scheduleRequestDto.getTaskIdentifier(),
        scheduleRequestDto.getTaskKey(),
        scheduleRequestDto.getTriggerTime(),
        scheduleRequestDto.getCronExpression(),
        scheduleRequestDto.getTaskData());
  }

  @Override
  public Endpoint mapEndpointFromDto(ScheduleRequestDto scheduleRequestDto) {
    if (scheduleRequestDto == null) {
      throw new SchedulerValidationException("scheduleRequestDto cannot be null");
    }
    if (scheduleRequestDto.getCallbackDescriptor() == null) {
      throw new SchedulerValidationException(
          "scheduleRequestDto callbackDescriptor cannot be null");
    }
    return new Endpoint(
        CallbackType.valueOf(scheduleRequestDto.getCallbackDescriptor().getType().toString()),
        scheduleRequestDto.getCallbackDescriptor().getEndpoint());
  }

  @Override
  public ScheduleTaskDto mapTasktoDto(Task task) {
    ScheduleTaskDto scheduleTaskDto = new ScheduleTaskDto();
    scheduleTaskDto.setTaskKey(task.getKey());
    scheduleTaskDto.setTaskIdentifier(task.getId());
    scheduleTaskDto.setTriggerTime(task.getTriggerTime());
    scheduleTaskDto.setCronExpression(task.getCronExpression());
    scheduleTaskDto.setTaskData(task.getTaskData());
    return scheduleTaskDto;
  }

  @Override
  public TaskStatsDto toTaskStatsDto(ScheduleJobMetadata scheduleJobMetadata) {
    TaskStatsDto taskStatsDto = new TaskStatsDto();
    taskStatsDto.setExecutions(scheduleJobMetadata.getExecutions());
    taskStatsDto.setLastTriggerTime(scheduleJobMetadata.getLastTriggerTime());
    taskStatsDto.setFailures(scheduleJobMetadata.getFailures());
    taskStatsDto.setLastFailureTime(scheduleJobMetadata.getLastFailureTime());
    return taskStatsDto;
  }
}
