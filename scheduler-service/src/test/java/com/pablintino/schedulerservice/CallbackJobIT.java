package com.pablintino.schedulerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pablintino.schedulerservice.configurations.InMemoryQuartzConfiguration;
import com.pablintino.schedulerservice.helpers.DummyCallbackService;
import com.pablintino.schedulerservice.helpers.DummyTaskDataModels;
import com.pablintino.schedulerservice.helpers.DummyTasksProvider;
import com.pablintino.schedulerservice.helpers.QuartzJobListener;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.JobParamsEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest()
@TestPropertySource(
    properties = {
      "com.pablintino.scheduler.failure-attempt-delay=1000",
      "com.pablintino.scheduler.failure-attempts=5",
    })
class CallbackJobIT {

  @Autowired private DummyCallbackService dummyCallbackService;

  @Autowired private Scheduler scheduler;

  @Autowired private DummyTasksProvider dummyTasksProvider;

  @Autowired private QuartzJobListener jobListener;

  @Value("${com.pablintino.scheduler.failure-attempt-delay}")
  private long failureAttemptDelay;

  @Value("${com.pablintino.scheduler.failure-attempts}")
  private int failureAttempts;

  @Autowired private IReeschedulableAnnotationResolver reeschedulableAnnotationResolver;

  @Configuration
  @Import(InMemoryQuartzConfiguration.class)
  static class TestConfiguration {

    @Bean
    public DummyTasksProvider dummyTasksProvider(JobParamsEncoder jobParamsEncoder) {
      return new DummyTasksProvider(jobParamsEncoder);
    }

    @Bean
    QuartzJobListener jobListener(Scheduler scheduler) throws SchedulerException {
      QuartzJobListener listener = new QuartzJobListener();
      scheduler.getListenerManager().addJobListener(listener);
      return listener;
    }

    @Bean
    public ObjectMapper objectMapper() {
      return Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule()).build();
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
    public IReeschedulableAnnotationResolver reeschedulableAnnotationResolver() {
      return Mockito.mock(IReeschedulableAnnotationResolver.class);
    }
  }

  @Test
  @DirtiesContext
  void simpleScheduledExecutionOK() throws SchedulerException {
    DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
    scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

    QuartzJobListener.JobExecutionEntry jobExecution = jobListener.waitJobExecution(1500);
    Assertions.assertNotNull(jobExecution);
    Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
    DummyCallbackService.CallbackCallEntry callbackCallEntry =
        dummyCallbackService.getExecutions().peek();
    dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry, jobExecution);
  }

  @Test
  @DirtiesContext
  void cronScheduledExecutionOK() throws SchedulerException {
    DummyTaskDataModels testModels =
        dummyTasksProvider.createCronValidJob("test-job1", 1000, " 0/2 * * * * ? *");
    scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

    List<QuartzJobListener.JobExecutionEntry> jobExecutions =
        jobListener.waitJobExecutions(2, 5000);
    Assertions.assertNotNull(jobExecutions);

    dummyTasksProvider.validateCronValidJob(
        testModels,
        dummyCallbackService.getExecutions().stream().collect(Collectors.toList()),
        jobExecutions);
  }

  @Test
  @DirtiesContext
  void simpleScheduledExecutionUnreschedulableKO() throws SchedulerException {
    dummyCallbackService.setCallback(
        (s, j) -> {
          throw new RuntimeException("test exception");
        });

    DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
    scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

    QuartzJobListener.JobExecutionEntry jobExecution = jobListener.waitJobExecution(1500);
    Assertions.assertNotNull(jobExecution);
    Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
    DummyCallbackService.CallbackCallEntry callbackCallEntry =
        dummyCallbackService.getExecutions().peek();
    dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry, jobExecution);

    Assertions.assertEquals(0, jobListener.getExecutions().size());
    Assertions.assertEquals(false, jobExecution.getEx().refireImmediately());
    Assertions.assertNull(scheduler.getTrigger(testModels.getTrigger().getKey()));
  }

  @Test
  @DirtiesContext
  void simpleScheduledExecutionReschedulableKO() throws SchedulerException {
    dummyCallbackService.setCallback(
        (s, j) -> {
          throw new IllegalArgumentException("test exception");
        });

    Mockito.when(reeschedulableAnnotationResolver.getAnnotatedTypes())
        .thenReturn(Collections.singleton(RuntimeException.class));

    DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
    scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

    List<QuartzJobListener.JobExecutionEntry> jobExecutions =
        jobListener.waitJobExecutions(
            failureAttempts + 2,
            Math.round(Math.ceil((failureAttempts + 1) * failureAttemptDelay * 1.1)));
    Assertions.assertEquals(failureAttempts + 1, jobExecutions.size());

    dummyTasksProvider.validateSimpleValidReattemptedNotRecoveredJob(
        testModels,
        dummyCallbackService.getExecutions().stream().collect(Collectors.toList()),
        jobExecutions,
        failureAttempts,
        failureAttemptDelay);

    Assertions.assertEquals(0, jobListener.getExecutions().size());
    Assertions.assertFalse(jobExecutions.stream().anyMatch(ex -> ex.getEx().refireImmediately()));

    /* Ensure trigger has been removed after non being able to recovering from failure */
    Assertions.assertNull(scheduler.getTrigger(testModels.getTrigger().getKey()));
  }

  @Test
  @DirtiesContext
  void cronScheduledExecutionRecoveredFailureOK() throws SchedulerException {
    dummyCallbackService.setCallback(
        (s, j) -> {
          if (s.getMetadata().getExecutions() < 3) {
            throw new RuntimeException("test exception");
          }
        });

    Mockito.when(reeschedulableAnnotationResolver.getAnnotatedTypes())
        .thenReturn(Collections.singleton(RuntimeException.class));

    DummyTaskDataModels testModels =
        dummyTasksProvider.createCronValidJob("test-job1", 1000, " 0/2 * * * * ? *");
    scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

    List<QuartzJobListener.JobExecutionEntry> jobExecutions =
        jobListener.waitJobExecutions(
            4, (int) Math.floor((Math.ceil((2) * failureAttemptDelay) + 2000 * 3) * 1.1));

    /* Ensure trigger has not being deleted */
    Assertions.assertNotNull(scheduler.getTrigger(testModels.getTrigger().getKey()));

    /* Delete trigger to prevent jobs to being launched while checking results*/
    scheduler.unscheduleJob(testModels.getTrigger().getKey());
    Assertions.assertEquals(4, jobExecutions.size());

    dummyTasksProvider.validateCronReattemptedAndRecoveredJob(
        testModels,
        dummyCallbackService.getExecutions().stream().collect(Collectors.toList()),
        jobExecutions,
        failureAttemptDelay,
        2);
  }
}
