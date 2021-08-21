package com.pablintino.schedulerservice.rest;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.services.ISchedulingService;
import com.pablintino.schedulerservice.services.mappers.ISchedulingDtoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    List<ScheduleTaskDto> getSchedulesForKey(@PathVariable("key") String key) {
        return schedulingService.getTasksForKey(key)
                .stream()
                .map(schedulingDtoMapper::mapTasktoDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/schedules/{key}/{id}")
    ResponseEntity<ScheduleTaskDto> getSchedule(@PathVariable("key") String key, @PathVariable("id") String id) {
        Task task = schedulingService.getTask(key, id);
        return new ResponseEntity(task, task != null ? HttpStatus.OK : HttpStatus.NOT_FOUND);
    }

    @DeleteMapping("/schedules/{key}/{id}")
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    void deleteTask(@PathVariable("key") String key, @PathVariable("id") String id) {
        schedulingService.deleteTask(key, id);
    }
}
