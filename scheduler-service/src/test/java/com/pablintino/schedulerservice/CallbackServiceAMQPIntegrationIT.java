package com.pablintino.schedulerservice;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.configurations.AmqpTestIntegrationConfiguration;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Import(AmqpTestIntegrationConfiguration.class)
class CallbackServiceAMQPIntegrationIT {
  @Autowired private ICallbackService callbackService;

  @Autowired private DirectExchange exchange;

  private BlockingQueue<CallbackMessage> messageQueue = new LinkedBlockingQueue<>();

  @Test
  @DisplayName(
      "Simple passing test that sends a controller payload that is expected to be received correctly")
  void simpleSendOK() throws InterruptedException {
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(1);
    scheduleEventMetadata.setLastTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            "test",
            AmqpTestIntegrationConfiguration.QUEUE_KEY,
            null,
            CallbackType.AMQP,
            scheduleEventMetadata);

    callbackService.executeCallback(schedulerJobData, map);

    CallbackMessage message = messageQueue.poll(10, TimeUnit.SECONDS);
    Assertions.assertNotNull(message);
    Assertions.assertEquals(schedulerJobData.getKey(), message.getKey());
    Assertions.assertEquals(schedulerJobData.getTaskId(), message.getId());
    Assertions.assertEquals(map.getWrappedMap(), message.getDataMap());
    Assertions.assertEquals(
        scheduleEventMetadata.getNotificationAttempt(), message.getNotificationAttempt());
    Assertions.assertEquals(scheduleEventMetadata.getLastTriggerTime(), message.getTriggerTime());
  }

  @RabbitListener(queues = {AmqpTestIntegrationConfiguration.QUEUE_KEY})
  public void queueListener(CallbackMessage callbackMessage) {
    try {
      messageQueue.put(callbackMessage);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
