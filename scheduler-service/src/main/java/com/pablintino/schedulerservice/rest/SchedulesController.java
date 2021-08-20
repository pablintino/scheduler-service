package com.pablintino.schedulerservice.rest;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.services.ISchedulingService;
import com.pablintino.schedulerservice.services.mappers.ISchedulingDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class SchedulesController {

    private final ISchedulingService schedulingService;
    private final ISchedulingDtoMapper schedulingDtoMapper;

    @PostMapping("/schedules")
    ScheduleRequestDto newScheduleRequest(@Valid @RequestBody ScheduleRequestDto scheduleRequest) {

        Task task = schedulingDtoMapper.mapTaskFromDto(scheduleRequest);
        Endpoint endpoint = schedulingDtoMapper.mapEndpointFromDto(scheduleRequest);
        schedulingService.scheduleTask(task, endpoint);

        return scheduleRequest;
    }

    @GetMapping("/schedules/{key}")
    List<ScheduleTaskDto> newScheduleRequest(@PathVariable("key") String key) {
        return schedulingService.getTasksForKey(key)
                .stream()
                .map(schedulingDtoMapper::mapTasktoDto)
                .collect(Collectors.toList());
    }

    @DeleteMapping("/schedules/{key}/{id}")
    void deleteTask(@PathVariable("key") String key, @PathVariable("id") String id) {
        schedulingService.deleteTask(key, id);
    }
}
