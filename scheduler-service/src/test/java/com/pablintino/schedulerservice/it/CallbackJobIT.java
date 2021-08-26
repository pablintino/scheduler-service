package com.pablintino.schedulerservice.it;

import com.pablintino.schedulerservice.it.configurations.InMemoryQuartzConfiguration;
import com.pablintino.schedulerservice.it.helpers.QuartzJobListener;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.Endpoint;
import com.pablintino.schedulerservice.models.Task;
import com.pablintino.schedulerservice.quartz.CallbackJob;
import com.pablintino.schedulerservice.quartz.annotations.IReeschedulableAnnotationResolver;
import com.pablintino.schedulerservice.services.ICallbackService;
import com.pablintino.schedulerservice.services.IJobParamsEncoder;
import com.pablintino.schedulerservice.services.JobParamsEncoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;

@SpringBootTest()
@TestPropertySource(
        properties = "com.pablintino.scheduler.failure-attempt-delay=1000"
)
class CallbackJobIT {

    @Autowired
    private ICallbackService callbackService;

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private IJobParamsEncoder jobParamsEncoder;

    @Configuration
    @Import(InMemoryQuartzConfiguration.class)
    static class TestConfiguration {

        @Bean
        public ICallbackService callbackService() {
            return Mockito.mock(ICallbackService.class);
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
    void simpleScheduledExecutionOK() throws SchedulerException {
        scheduleSimpleJob(scheduler, "test-job1", 1000);

        Mockito.verify(callbackService, Mockito.timeout(1500).times(1)).executeCallback(ArgumentMatchers.any(), ArgumentMatchers.any());

        // TODO Assert passed data
    }

    @Test
    void simpleScheduledExecutionUnreschedulableKO() throws SchedulerException {
        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        scheduleSimpleJob(scheduler, "test-job1", 1000);
        Mockito.doThrow(new RuntimeException("test exception")).when(callbackService).executeCallback(ArgumentMatchers.any(), ArgumentMatchers.any());

        QuartzJobListener.JobExecutionEntry jobExecution = listener.waitJobExecution(1500);
        Assertions.assertNotNull(jobExecution);
        Assertions.assertEquals(0, listener.getExecutions().size());
        Assertions.assertEquals(false, jobExecution.ex().refireImmediately());
        Assertions.assertEquals(true, jobExecution.ex().unscheduleAllTriggers());
    }


    private void scheduleSimpleJob(Scheduler scheduler, String name, long triggerTime) throws SchedulerException {

        Date startDate = Date.from(Instant.now().plusMillis(triggerTime));
        Trigger trigger = TriggerBuilder
                .newTrigger()
                .withIdentity(name, "it-test")
                .startAt(startDate)
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()).build();

        Task dummyTask = new Task(name, "it-test",
                ZonedDateTime.ofInstant(startDate.toInstant(), ZoneOffset.UTC),
                null,
                Collections.emptyMap());

        Endpoint dummyEndpoint = new Endpoint(CallbackType.AMQP, null);


        JobDataMap dataMap = new JobDataMap();
        dataMap.putAll(jobParamsEncoder.encodeJobParameters(dummyTask, dummyEndpoint));

        JobDetail job = JobBuilder
                .newJob(CallbackJob.class)
                .withIdentity(dummyTask.id(), dummyTask.key())
                .setJobData(dataMap)
                .build();

        scheduler.scheduleJob(job, trigger);
    }
}
