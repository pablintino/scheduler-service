package com.pablintino.schedulerservice;

import com.pablintino.schedulerservice.configurations.InMemoryQuartzConfiguration;
import com.pablintino.schedulerservice.helpers.DummyCallbackService;
import com.pablintino.schedulerservice.helpers.DummyTaskDataModels;
import com.pablintino.schedulerservice.helpers.DummyTasksProvider;
import com.pablintino.schedulerservice.helpers.QuartzJobListener;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.quartz.annotations.ReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import com.pablintino.schedulerservice.services.ISchedulingService;
import com.pablintino.schedulerservice.services.JobParamsEncoder;
import com.pablintino.schedulerservice.services.SchedulingService;
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
class SchedulerServiceIT {

    @Autowired
    private DummyCallbackService dummyCallbackService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private ISchedulingService schedulingService;

    @Autowired
    private DummyTasksProvider dummyTasksProvider;

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
            return new ReeschedulableAnnotationResolver();
        }

        @Bean
        public ISchedulingService schedulingService(Scheduler scheduler, IJobParamsEncoder jobParamsEncoder) {
            return new SchedulingService(scheduler, jobParamsEncoder);
        }
    }

    @Test
    @DirtiesContext
    void simpleScheduledExecutionOK() throws SchedulerException {
        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        schedulingService.scheduleTask(testModels.getTask(), testModels.getEndpoint());

        QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(1500);
        Assertions.assertNotNull(jobExecution);
        Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
        DummyCallbackService.CallbackCallEntry callbackCallEntry = dummyCallbackService.getExecutions().peek();
        dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());
    }
}
