package com.pablintino.schedulerservice.ut.services;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.CallbackService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
public class CallbackServiceTest {

  private final String DUMMY_EXCHANGE_NAME = "exchange-name";
  private final String DUMMY_KEY_NAME = "test-key";
  private final String DUMMY_ID_NAME = "test-id";

  @Mock RabbitTemplate rabbitTemplate;
  @Mock WebClient.Builder webClientBuilder;
  @Mock WebClient webClient;

  @BeforeEach
  public void beforeTest() {
    Mockito.when(webClientBuilder.defaultHeader(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(webClientBuilder);
    Mockito.when(webClientBuilder.build()).thenReturn(webClient);
  }

  @Test
  void simpleAMQPCallbackSendOK() {
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, webClientBuilder, DUMMY_EXCHANGE_NAME, 1000L);

    /** Prepare the dummy data to be sent */
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(0);
    scheduleEventMetadata.setLastTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP, scheduleEventMetadata);

    /** Call the service to enqueue the AMPQ message */
    callbackService.executeCallback(schedulerJobData, map);

    /** Verify the expected enqueued data */
    CallbackMessage expectedMessage =
        new CallbackMessage(
            DUMMY_ID_NAME,
            DUMMY_KEY_NAME,
            map.getWrappedMap(),
            scheduleEventMetadata.getLastTriggerTime(),
            scheduleEventMetadata.getNotificationAttempt());
    Mockito.verify(rabbitTemplate, Mockito.times(1))
        .convertAndSend(DUMMY_EXCHANGE_NAME, DUMMY_KEY_NAME, expectedMessage);

    Mockito.verifyNoInteractions(webClient);
  }

  @Test
  void simpleAMQPCallbackSendFailSendKO() {
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, webClientBuilder, DUMMY_EXCHANGE_NAME, 1000);

    /** Prepare the dummy data to be sent */
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(0);
    scheduleEventMetadata.setLastTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP, scheduleEventMetadata);

    /** Simulate an exception thrown while sending the callback message */
    Mockito.doThrow(new AmqpException("test exception"))
        .when(rabbitTemplate)
        .convertAndSend(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(CallbackMessage.class));

    /** Assert that the exception is not replaced or masquerade with any other one */
    Assertions.assertThrows(
        AmqpException.class,
        () -> {
          callbackService.executeCallback(schedulerJobData, map);
        });

    Mockito.verifyNoInteractions(webClient);
  }
}
