package com.pablintino.schedulerservice;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.configurations.AmqpTestIntegrationConfiguration;
import com.pablintino.schedulerservice.helpers.HttpCallbackMockController;
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
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Import(AmqpTestIntegrationConfiguration.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"com.pablintino.scheduler.http.callback-timeout:5000"})
class CallbackServiceIT {
  @Autowired private ICallbackService callbackService;
  @Autowired private DirectExchange exchange;
  @Autowired private HttpCallbackMockController httpCallbackMockController;

  @LocalServerPort private int serverPort;

  private BlockingQueue<CallbackMessage> amqpListenerQueue = new LinkedBlockingQueue<>();

  @Test
  @DirtiesContext
  @DisplayName("Positive test that verifies AMQP callback")
  void simpleSendAmqpOK() throws InterruptedException, UnknownHostException {
    createAssertSimpleSendOK(amqpListenerQueue, CallbackType.AMQP);
  }

  @Test
  @DirtiesContext
  @DisplayName("Positive test that verifies HTTP callback")
  void simpleSendHttpOK() throws InterruptedException, UnknownHostException {
    createAssertSimpleSendOK(httpCallbackMockController.getMessageQueue(), CallbackType.HTTP);
  }

  private void createAssertSimpleSendOK(
      BlockingQueue<CallbackMessage> messageQueue, CallbackType type)
      throws InterruptedException, UnknownHostException {
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(1);
    scheduleEventMetadata.setTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            "test",
            AmqpTestIntegrationConfiguration.QUEUE_KEY,
            type == CallbackType.HTTP
                ? "http://"
                    + InetAddress.getLocalHost().getHostName()
                    + ":"
                    + serverPort
                    + "/test/success"
                : null,
            type,
            scheduleEventMetadata);

    callbackService.executeCallback(schedulerJobData, map);

    CallbackMessage message = messageQueue.poll(5, TimeUnit.SECONDS);
    Assertions.assertNotNull(message);
    Assertions.assertEquals(schedulerJobData.getKey(), message.getKey());
    Assertions.assertEquals(schedulerJobData.getTaskId(), message.getId());
    Assertions.assertEquals(map.getWrappedMap(), message.getDataMap());
    Assertions.assertEquals(
        scheduleEventMetadata.getNotificationAttempt(), message.getNotificationAttempt());
    Assertions.assertEquals(scheduleEventMetadata.getTriggerTime(), message.getTriggerTime());
  }

  @RabbitListener(queues = {AmqpTestIntegrationConfiguration.QUEUE_KEY})
  public void queueListener(CallbackMessage callbackMessage) {
    try {
      amqpListenerQueue.put(callbackMessage);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
