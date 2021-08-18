package com.pablintino.schedulerservice.services.mappers;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import org.springframework.stereotype.Component;

@Component
public class SchedulingDtoMapper implements ISchedulingDtoMapper{

    @Override
    public Task mapTaskFromDto(ScheduleRequestDto scheduleRequestDto){
        return new Task(
                scheduleRequestDto.getTaskIdentifier(),
                scheduleRequestDto.getTaskKey(),
                scheduleRequestDto.getTriggerTime(),
                scheduleRequestDto.getCronExpression(),
                scheduleRequestDto.getTaskData()
                );
    }

    @Override
    public Endpoint mapEndpointFromDto(ScheduleRequestDto scheduleRequestDto){
        return new Endpoint(
                CallbackType.valueOf(scheduleRequestDto.getCallbackDescriptor().getType().toString()),
                scheduleRequestDto.getCallbackDescriptor().getEndpoint()
        );
    }

    @Override
    public ScheduleTaskDto mapTasktoDto(Task task){
        return new ScheduleTaskDto(
                task.id(),
                task.key(),
                task.triggerTime(),
                task.cronExpression(),
                task.taskData()
        );
    }
}
