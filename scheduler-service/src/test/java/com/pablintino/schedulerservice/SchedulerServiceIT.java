package com.pablintino.schedulerservice;

import com.pablintino.schedulerservice.configurations.InMemoryQuartzConfiguration;
import com.pablintino.schedulerservice.helpers.DummyCallbackService;
import com.pablintino.schedulerservice.helpers.DummyTaskDataModels;
import com.pablintino.schedulerservice.helpers.DummyTasksProvider;
import com.pablintino.schedulerservice.helpers.QuartzJobListener;
import com.pablintino.schedulerservice.models.Task;
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
import java.util.UUID;
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
    void simpleScheduleTaskOK() throws SchedulerException {
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

    @Test
    @DirtiesContext
    void getTasksOK() throws SchedulerException {

        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 2000);
        schedulingService.scheduleTask(testModels.getTask(), testModels.getEndpoint());

        DummyTaskDataModels testModels2 = dummyTasksProvider.createSimpleValidJob("test-job2", 1000);
        schedulingService.scheduleTask(testModels2.getTask(), testModels2.getEndpoint());

        List<Task> tasks = schedulingService.getTasksForKey(testModels.getTask().getKey());
        Assertions.assertEquals(2, tasks.size());
        Assertions.assertEquals(testModels.getTask(), tasks.stream().filter(t -> testModels.getTask().getId().equals(t.getId())).findFirst().orElse(null));
        Assertions.assertEquals(testModels2.getTask(), tasks.stream().filter(t -> testModels2.getTask().getId().equals(t.getId())).findFirst().orElse(null));
        List<QuartzJobListener.JobExecutionEntry> jobExecutions= listener.waitJobExecutions(2, 3000);
        Assertions.assertEquals(2, jobExecutions.size());

        DummyCallbackService.CallbackCallEntry callbackCallEntry = dummyCallbackService.getExecutions().peek();
        if(testModels.getTask().getId().equals(callbackCallEntry.getJobData().getTaskId())){
            dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());
        }else if(testModels2.getTask().getId().equals(callbackCallEntry.getJobData().getTaskId())){
            dummyTasksProvider.validateSimpleValidJob(testModels2, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());
        }else {
            Assertions.fail("Unexpected task");
        }
    }

    @Test
    @DirtiesContext
    void deleteTaskOK() throws SchedulerException {

        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        schedulingService.scheduleTask(testModels.getTask(), testModels.getEndpoint());

        List<Task> tasks = schedulingService.getTasksForKey(testModels.getTask().getKey());
        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals(testModels.getTask(), tasks.stream().filter(t -> testModels.getTask().getId().equals(t.getId())).findFirst().orElse(null));

        schedulingService.deleteTask(testModels.getTask().getKey(), testModels.getTask().getId());

        List<QuartzJobListener.JobExecutionEntry> jobExecutions= listener.waitJobExecutions(1, 3000);
        Assertions.assertEquals(0, jobExecutions.size());
    }

    @Test
    @DirtiesContext
    void getTaskOK() throws SchedulerException {

        QuartzJobListener listener = new QuartzJobListener();
        scheduler.getListenerManager().addJobListener(listener);

        DummyTaskDataModels testModels = dummyTasksProvider.createSimpleValidJob("test-job1", 1000);
        schedulingService.scheduleTask(testModels.getTask(), testModels.getEndpoint());

        Task task = schedulingService.getTask(testModels.getTask().getKey(), testModels.getTask().getId());
        Assertions.assertNotNull(task);
        Assertions.assertEquals(testModels.getTask(), task);

        Assertions.assertNull(schedulingService.getTask(UUID.randomUUID().toString(), testModels.getTask().getId()));

        List<QuartzJobListener.JobExecutionEntry> jobExecutions= listener.waitJobExecutions(2, 2000);
        Assertions.assertEquals(1, jobExecutions.size());

        Assertions.assertEquals(1, dummyCallbackService.getExecutions().size());
        DummyCallbackService.CallbackCallEntry callbackCallEntry = dummyCallbackService.getExecutions().peek();

        dummyTasksProvider.validateSimpleValidJob(testModels, callbackCallEntry.getJobData(), callbackCallEntry.getJobDataMap(), callbackCallEntry.getScheduleEventMetadata().getTriggerTime());
    }
}
