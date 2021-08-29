package com.pablintino.schedulerservice;

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
        }
)
class CallbackJobIT {

    @Autowired
    private DummyCallbackService dummyCallbackService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private DummyTasksProvider dummyTasksProvider;

    @Value("${com.pablintino.scheduler.failure-attempt-delay}")
    private long failureAttemptDelay;

    @Value("${com.pablintino.scheduler.failure-attempts}")
    private int failureAttempts;


    @Autowired
    private IReeschedulableAnnotationResolver reeschedulableAnnotationResolver;

    @Configuration
    @Import(InMemoryQuartzConfiguration.class)
    static class TestConfiguration {

        @Bean
        public DummyTasksProvider dummyTasksProvider(JobParamsEncoder jobParamsEncoder){
            return new DummyTasksProvider(jobParamsEncoder);
        }

        @Bean
        public DummyCallbackService callbackService() {
            return new DummyCallbackService();
        }

        @Bean
        public JobParamsEncoder jobParamsEncoder() {
            return new JobParamsEncoder();
        }

        @Bean
        public IReeschedulableAnnotationResolver reeschedulableAnnotationResolver() {
            return Mockito.mock(IReeschedulableAnnotationResolver.class);
        }
    }

    @Test
    @DirtiesContext
    void simpleScheduledExecutionOK() throws SchedulerException {
        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

        QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(1500);
        Assertions.assertNotNull(jobExecution);
        Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
        DummyCallbackService.CallbackCallEntry callbackCallEntry = dummyCallbackService.getExecutions().peek();
        dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());

    }

    @Test
    @DirtiesContext
    void simpleScheduledExecutionUnreschedulableKO() throws SchedulerException {
        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        dummyCallbackService.setCallback((s,j)->{
            throw new RuntimeException("test exception");
        });

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());

        QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(1500);
        Assertions.assertNotNull(jobExecution);
        Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
        DummyCallbackService.CallbackCallEntry callbackCallEntry = dummyCallbackService.getExecutions().peek();
        dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());

        Assertions.assertEquals(0, listener.getExecutions().size());
        Assertions.assertEquals(false, jobExecution.getEx().refireImmediately());
        Assertions.assertEquals(true, jobExecution.getEx().unscheduleAllTriggers());
    }

    @Test
    @DirtiesContext
    void simpleScheduledExecutionReschedulableKO() throws SchedulerException {
        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        dummyCallbackService.setCallback((s,j)->{
            throw new IllegalArgumentException("test exception");
        });

        Mockito.when(reeschedulableAnnotationResolver.getAnnotatedTypes()).thenReturn(Collections.singleton(RuntimeException.class));

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        scheduler.scheduleJob(testModels.getJobDetail(), testModels.getTrigger());


        List<QuartzJobListener.JobExecutionEntry> jobExecutions= listener.waitJobExecutions(failureAttempts + 2, Math.round(Math.ceil((failureAttempts + 1)*failureAttemptDelay*1.1)));
        Assertions.assertEquals(failureAttempts + 1, jobExecutions.size());
        Assertions.assertEquals(failureAttempts + 1, dummyCallbackService.getExecutions().size());

        dummyTasksProvider.validateSimpleValidReattemptedJob( testModels, dummyCallbackService.getExecutions().stream().collect(Collectors.toList()), failureAttempts, failureAttemptDelay);
    }
}
