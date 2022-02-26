package com.pablintino.schedulerservice.ut.services;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.quartz.annotations.Reeschedulable;
import com.pablintino.schedulerservice.services.CallbackService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeoutException;

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
    scheduleEventMetadata.setTriggerTime(Instant.now());
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
            scheduleEventMetadata.getTriggerTime(),
            scheduleEventMetadata.getNotificationAttempt());
    Mockito.verify(rabbitTemplate, Mockito.times(1))
        .convertAndSend(DUMMY_EXCHANGE_NAME, DUMMY_KEY_NAME, expectedMessage);

    Mockito.verifyNoInteractions(webClient);
  }

  @Test
  void simpleHTTPCallbackSendOK() {
    long callbackTimeout = 1000L;
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, webClientBuilder, DUMMY_EXCHANGE_NAME, callbackTimeout);

    /** Prepare the dummy data to be sent */
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(0);
    scheduleEventMetadata.setTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            DUMMY_ID_NAME,
            DUMMY_KEY_NAME,
            "http://test.host.com:8080/test",
            CallbackType.HTTP,
            scheduleEventMetadata);

    WebClient.RequestBodyUriSpec bodySpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
    WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);
    Mono mono = Mockito.mock(Mono.class);
    Mockito.when(responseSpec.bodyToMono(ArgumentMatchers.any(Class.class))).thenReturn(mono);
    Mockito.when(bodySpec.retrieve()).thenReturn(responseSpec);
    Mockito.when(bodySpec.uri(ArgumentMatchers.anyString())).thenReturn(bodySpec);
    Mockito.when(mono.timeout(ArgumentMatchers.any(Duration.class))).thenReturn(mono);
    Mockito.when(bodySpec.body(ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
        .thenReturn(bodySpec);
    Mockito.when(webClient.post()).thenReturn(bodySpec);

    /* Call the service to enqueue the AMPQ message */
    callbackService.executeCallback(schedulerJobData, map);

    ArgumentCaptor<Mono> webClientBodyCaptor = ArgumentCaptor.forClass(Mono.class);
    Mockito.verify(bodySpec, Mockito.times(1))
        .body(webClientBodyCaptor.capture(), ArgumentMatchers.any(Class.class));

    /* Verify that the payload of the HTTP is a proper callback message*/
    CallbackMessage capturedMessage = (CallbackMessage) webClientBodyCaptor.getValue().block();
    CallbackMessage expectedMessage =
        new CallbackMessage(
            DUMMY_ID_NAME,
            DUMMY_KEY_NAME,
            map.getWrappedMap(),
            scheduleEventMetadata.getTriggerTime(),
            scheduleEventMetadata.getNotificationAttempt());

    Assertions.assertEquals(expectedMessage, capturedMessage);

    /* Verify that the HTTP timeout is provided to the client */
    Mockito.verify(mono, Mockito.times(1)).timeout(Duration.of(callbackTimeout, ChronoUnit.MILLIS));

    /* Verify WebClient block is called */
    Mockito.verify(mono, Mockito.times(1)).block();

    /* Verify AMPQ is not used */
    Mockito.verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void httpCallbackSendFailureCasesKO() {
    long callbackTimeout = 1000L;
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, webClientBuilder, DUMMY_EXCHANGE_NAME, callbackTimeout);

    /** Prepare the dummy data to be sent */
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(0);
    scheduleEventMetadata.setTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            DUMMY_ID_NAME,
            DUMMY_KEY_NAME,
            "http://test.host.com:8080/test",
            CallbackType.HTTP,
            scheduleEventMetadata);

    WebClient.RequestBodyUriSpec bodySpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
    WebClient.ResponseSpec responseSpec = Mockito.mock(WebClient.ResponseSpec.class);
    Mono mono = Mockito.mock(Mono.class);
    Mockito.when(responseSpec.bodyToMono(ArgumentMatchers.any(Class.class))).thenReturn(mono);
    Mockito.when(bodySpec.retrieve()).thenReturn(responseSpec);
    Mockito.when(bodySpec.uri(ArgumentMatchers.anyString())).thenReturn(bodySpec);
    Mockito.when(mono.timeout(ArgumentMatchers.any(Duration.class))).thenReturn(mono);
    Mockito.when(bodySpec.body(ArgumentMatchers.any(), ArgumentMatchers.any(Class.class)))
        .thenReturn(bodySpec);
    Mockito.when(webClient.post()).thenReturn(bodySpec);

    /* Simulate a non-recoverable exception thrown while sending the callback message */
    Mockito.doThrow(new RuntimeException("test exception")).when(mono).block();

    /* Assert that a non-recoverable exception is not replaced or masquerade with any other one */
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          callbackService.executeCallback(schedulerJobData, map);
        });

    /* Simulate a non-recoverable exception (derived from a 4xx return code) thrown while sending the callback message */
    Mockito.doThrow(
            new WebClientResponseException(
                HttpStatus.UNAUTHORIZED.value(), "test", null, null, null))
        .when(mono)
        .block();

    /* Assert that a non-recoverable exception is not replaced or masquerade with any other one */
    Assertions.assertThrows(
        RuntimeException.class,
        () -> {
          callbackService.executeCallback(schedulerJobData, map);
        });

    /* Verify reeschedulable exceptions */
    callAssertReeschedulableLaunched(
        callbackService,
        map,
        schedulerJobData,
        mono,
        new WebClientResponseException(
            HttpStatus.INTERNAL_SERVER_ERROR.value(), "test", null, null, null));
    callAssertReeschedulableLaunched(
        callbackService, map, schedulerJobData, mono, new RuntimeException(new TimeoutException()));
    callAssertReeschedulableLaunched(
        callbackService,
        map,
        schedulerJobData,
        mono,
        new RuntimeException(new UnknownHostException()));
    callAssertReeschedulableLaunched(
        callbackService,
        map,
        schedulerJobData,
        mono,
        new RuntimeException(PrematureCloseException.TEST_EXCEPTION));

    /* Verify AMPQ is not used */
    Mockito.verifyNoInteractions(rabbitTemplate);
  }

  @Test
  void amqpCallbackSendFailureCasesKO() {
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, webClientBuilder, DUMMY_EXCHANGE_NAME, 1000);

    /** Prepare the dummy data to be sent */
    JobDataMap map = new JobDataMap();
    map.put("test-key", "test");
    ScheduleJobMetadata scheduleEventMetadata = new ScheduleJobMetadata();
    scheduleEventMetadata.setNotificationAttempt(0);
    scheduleEventMetadata.setTriggerTime(Instant.now());
    SchedulerJobData schedulerJobData =
        new SchedulerJobData(
            DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP, scheduleEventMetadata);

    /* Simulate a non-recoverable exception thrown while sending the callback message */
    Mockito.doThrow(new AmqpException("test exception"))
        .when(rabbitTemplate)
        .convertAndSend(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(CallbackMessage.class));

    /* Assert that a non-recoverable exception is not replaced or masquerade with any other one */
    Assertions.assertThrows(
        AmqpException.class,
        () -> {
          callbackService.executeCallback(schedulerJobData, map);
        });

    /* Recoverable exception cases */
    /* Simulate a recoverable IOException exception thrown while sending the callback message */
    Mockito.doThrow(new AmqpException(new IOException()))
        .when(rabbitTemplate)
        .convertAndSend(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(CallbackMessage.class));

    /* Assert that a recoverable exception is masked with a recoverable one */
    RuntimeException exception =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              callbackService.executeCallback(schedulerJobData, map);
            });

    /* Ensure that the threw exception is a recoverable one */
    Assertions.assertNotNull(
        AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));

    /* Simulate a recoverable ConnectException exception thrown while sending the callback message */
    Mockito.doThrow(new AmqpException(new ConnectException()))
        .when(rabbitTemplate)
        .convertAndSend(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(CallbackMessage.class));

    /* Assert that a recoverable exception is masked with a recoverable one */
    exception =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              callbackService.executeCallback(schedulerJobData, map);
            });

    /* Ensure that the threw exception is a recoverable one */
    Assertions.assertNotNull(
        AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));

    Mockito.verifyNoInteractions(webClient);
  }

  private void callAssertReeschedulableLaunched(
      CallbackService callbackService,
      JobDataMap map,
      SchedulerJobData schedulerJobData,
      Mono mono,
      RuntimeException cause) {
    Mockito.doThrow(cause).when(mono).block();

    RuntimeException exception =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              callbackService.executeCallback(schedulerJobData, map);
            });

    /* Ensure that the threw exception is a recoverable one */
    Assertions.assertNotNull(
        AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));
  }
}
