package com.pablintino.schedulerservice.services.mappers;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;

public interface ISchedulingDtoMapper {
    Task mapTaskFromDto(ScheduleRequestDto scheduleRequestDto);

    Endpoint mapEndpointFromDto(ScheduleRequestDto scheduleRequestDto);

    ScheduleTaskDto mapTasktoDto(Task task);
}
