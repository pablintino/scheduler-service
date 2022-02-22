package com.pablintino.schedulerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pablintino.schedulerservice.config.ExceptionControllerAdvice;
import com.pablintino.schedulerservice.configurations.InMemoryQuartzConfiguration;
import com.pablintino.schedulerservice.helpers.DummyCallbackService;
import com.pablintino.schedulerservice.helpers.DummyTaskDataModels;
import com.pablintino.schedulerservice.helpers.DummyTasksProvider;
import com.pablintino.schedulerservice.helpers.QuartzJobListener;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.quartz.annotations.ReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.rest.SchedulesController;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import com.pablintino.schedulerservice.services.ISchedulingService;
import com.pablintino.schedulerservice.services.JobParamsEncoder;
import com.pablintino.schedulerservice.services.SchedulingService;
import com.pablintino.schedulerservice.services.mappers.ISchedulingDtoMapper;
import com.pablintino.schedulerservice.services.mappers.SchedulingDtoMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;

@SpringBootTest
@AutoConfigureMockMvc
class SchedulesControllerIT {

  private ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Autowired private DummyCallbackService dummyCallbackService;

  @Autowired private Scheduler scheduler;

  @Autowired private DummyTasksProvider dummyTasksProvider;

  @Autowired private ExceptionControllerAdvice exceptionControllerAdvice;

  @Autowired private SchedulesController schedulesController;

  private MockMvc mockMvc;

  @BeforeEach
  public void before() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(schedulesController)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setViewResolvers((viewName, locale) -> new MappingJackson2JsonView())
            .setControllerAdvice(exceptionControllerAdvice)
            .build();
  }

  @EnableWebMvc
  @Configuration
  @Import(InMemoryQuartzConfiguration.class)
  static class TestConfiguration {

    @Bean
    public DummyTasksProvider dummyTasksProvider(JobParamsEncoder jobParamsEncoder) {
      return new DummyTasksProvider(jobParamsEncoder);
    }

    @Bean
    public DummyCallbackService callbackService(ObjectMapper objectMapper) {
      return new DummyCallbackService(objectMapper);
    }

    @Bean
    public JobParamsEncoder jobParamsEncoder(ObjectMapper objectMapper) {
      return new JobParamsEncoder(objectMapper);
    }

    @Bean
    public ObjectMapper objectMapper() {
      return Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule()).build();
    }

    @Bean
    public IReeschedulableAnnotationResolver reeschedulableAnnotationResolver() {
      return new ReeschedulableAnnotationResolver();
    }

    @Bean
    public ExceptionControllerAdvice exceptionControllerAdvice() {
      return new ExceptionControllerAdvice(true);
    }

    @Bean
    public SchedulesController schedulesController(
        ISchedulingService schedulingService, ISchedulingDtoMapper schedulingDtoMapper) {
      return new SchedulesController(schedulingService, schedulingDtoMapper);
    }

    @Bean
    public ISchedulingDtoMapper schedulingDtoMapper() {
      return new SchedulingDtoMapper();
    }

    @Bean
    public ISchedulingService schedulingService(
        Scheduler scheduler, IJobParamsEncoder jobParamsEncoder) {
      return new SchedulingService(scheduler, jobParamsEncoder);
    }
  }

  @Test
  @DirtiesContext
  void postTaskOK() throws Exception {
    QuartzJobListener listener = new QuartzJobListener();
    scheduler.getListenerManager().addJobListener(listener);

    DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 2000);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/schedules")
                .content(objectMapper.writeValueAsString(testModels.getScheduleRequestDto()))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.taskIdentifier", Matchers.is(testModels.getTask().getId())))
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.taskKey", Matchers.is(testModels.getTask().getKey())));

    QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(3000);
    Assertions.assertNotNull(jobExecution);
    Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
    DummyCallbackService.CallbackCallEntry callbackCallEntry =
        dummyCallbackService.getExecutions().peek();
    dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry, jobExecution);
  }

  @Test
  @DirtiesContext
  void deleteValidTaskOK() throws Exception {
    QuartzJobListener listener = new QuartzJobListener();
    scheduler.getListenerManager().addJobListener(listener);

    DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 3000);

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/schedules")
                .content(objectMapper.writeValueAsString(testModels.getScheduleRequestDto()))
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.taskIdentifier", Matchers.is(testModels.getTask().getId())))
        .andExpect(
            MockMvcResultMatchers.jsonPath(
                "$.taskKey", Matchers.is(testModels.getTask().getKey())));

    mockMvc
        .perform(
            MockMvcRequestBuilders.delete(
                "/schedules/{key}/{id}",
                testModels.getTask().getKey(),
                testModels.getTask().getId()))
        .andExpect(MockMvcResultMatchers.status().isNoContent());

    QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(3000);
    Assertions.assertNull(jobExecution);
    Assertions.assertEquals(0, dummyCallbackService.getExecutions().size());
  }
}
