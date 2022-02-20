package com.pablintino.schedulerservice.rest;

import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import com.pablintino.schedulerservice.dtos.ScheduleTaskDto;
import com.pablintino.schedulerservice.dtos.TaskStatsDto;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.services.ISchedulingService;
import com.pablintino.schedulerservice.services.mappers.ISchedulingDtoMapper;
import com.pablintino.services.commons.exceptions.ResourceNotFoundHttpServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
  public ScheduleRequestDto newScheduleRequest(
      @Valid @RequestBody ScheduleRequestDto scheduleRequest) {

    Task task = schedulingDtoMapper.mapTaskFromDto(scheduleRequest);
    Endpoint endpoint = schedulingDtoMapper.mapEndpointFromDto(scheduleRequest);
    schedulingService.scheduleTask(task, endpoint);

    return scheduleRequest;
  }

  @GetMapping("/schedules/{key}")
  public List<ScheduleTaskDto> getSchedulesForKey(@PathVariable("key") String key) {
    return schedulingService.getTasksForKey(key).stream()
        .map(schedulingDtoMapper::mapTasktoDto)
        .collect(Collectors.toList());
  }

  @GetMapping("/schedules/{key}/{id}")
  public ScheduleTaskDto getSchedule(
      @PathVariable("key") String key, @PathVariable("id") String id) {
    Task task = schedulingService.getTask(key, id);
    if (task == null) {
      throw new ResourceNotFoundHttpServiceException(
          "Schedule task with key " + key + " and id " + id + " was not found");
    }
    return schedulingDtoMapper.mapTasktoDto(task);
  }

  @GetMapping("/schedules/{key}/{id}/stats")
  public TaskStatsDto getScheduleStats(
      @PathVariable("key") String key, @PathVariable("id") String id) {
    ScheduleJobMetadata scheduleJobMetadata = schedulingService.getSchedulerJobMetadata(key, id);
    if (scheduleJobMetadata == null) {
      throw new ResourceNotFoundHttpServiceException(
          "Schedule task with key " + key + " and id " + id + " was not found");
    }
    return schedulingDtoMapper.toTaskStatsDto(scheduleJobMetadata);
  }

  @DeleteMapping("/schedules/{key}/{id}")
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  public void deleteTask(@PathVariable("key") String key, @PathVariable("id") String id) {
    schedulingService.deleteTask(key, id);
  }
}
