package com.pablintino.schedulerservice.ut.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.ScheduleJobMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.quartz.annotations.Reeschedulable;
import com.pablintino.schedulerservice.services.CallbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@ExtendWith(MockitoExtension.class)
public class CallbackServiceTest {

  private final String DUMMY_EXCHANGE_NAME = "exchange-name";
  private final String DUMMY_KEY_NAME = "test-key";
  private final String DUMMY_ID_NAME = "test-id";

  @Mock RabbitTemplate rabbitTemplate;
  @Mock HttpClient httpClient;
  @Mock ObjectMapper objectMapper;
  private MockedStatic<HttpClient> builderMock;

  @BeforeEach
  public void beforeTest() {
    builderMock = Mockito.mockStatic(HttpClient.class);
    HttpClient.Builder clientBuilderMock = Mockito.mock(HttpClient.Builder.class);
    builderMock.when(HttpClient::newBuilder).thenReturn(clientBuilderMock);
    Mockito.when(clientBuilderMock.connectTimeout(ArgumentMatchers.any()))
        .thenReturn(clientBuilderMock);
    Mockito.when(clientBuilderMock.build()).thenReturn(httpClient);
  }

  @AfterEach
  public void afterTest() {
    builderMock.close();
  }

  @Test
  void simpleAMQPCallbackSendOK() {
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, objectMapper, DUMMY_EXCHANGE_NAME, 1000L);

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

    Mockito.verifyNoInteractions(httpClient);
  }

  @Test
  void simpleHTTPCallbackSendOK() throws IOException, InterruptedException {
    long callbackTimeout = 1000L;
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, objectMapper, DUMMY_EXCHANGE_NAME, callbackTimeout);

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

    String dummyJson = "{\"test\":\"value\"}";
    Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any())).thenReturn(dummyJson);

    HttpRequest.Builder httpRequestBuilderMock = Mockito.mock(HttpRequest.Builder.class);
    Mockito.when(httpRequestBuilderMock.POST(ArgumentMatchers.any()))
        .thenReturn(httpRequestBuilderMock);
    Mockito.when(httpRequestBuilderMock.timeout(ArgumentMatchers.any()))
        .thenReturn(httpRequestBuilderMock);
    Mockito.when(
            httpRequestBuilderMock.header(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(httpRequestBuilderMock);
    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    Mockito.when(httpRequestBuilderMock.build()).thenReturn(mockRequest);
    try (MockedStatic<HttpRequest> httpRequestStaticMock = Mockito.mockStatic(HttpRequest.class)) {
      httpRequestStaticMock
          .when(() -> HttpRequest.newBuilder(ArgumentMatchers.any(URI.class)))
          .thenReturn(httpRequestBuilderMock);

      HttpResponse<Object> mockResponse = Mockito.mock(HttpResponse.class);
      Mockito.when(mockResponse.statusCode()).thenReturn(200);
      Mockito.when(httpClient.send(ArgumentMatchers.eq(mockRequest), ArgumentMatchers.any()))
          .thenReturn(mockResponse);

      /* Call the service to enqueue the HTTP message */
      callbackService.executeCallback(schedulerJobData, map);

      /* Verify that the HTTP timeout is provided to the client */
      Mockito.verify(httpRequestBuilderMock, Mockito.times(1))
          .timeout(Duration.of(callbackTimeout, ChronoUnit.MILLIS));

      /* Verify headers are added */
      Mockito.verify(httpRequestBuilderMock, Mockito.times(1))
          .header(
              ArgumentMatchers.eq(HttpHeaders.CONTENT_TYPE),
              ArgumentMatchers.eq(MediaType.APPLICATION_JSON_VALUE));

      /* Verify WebClient block is called */
      Mockito.verify(httpClient, Mockito.times(1))
          .send(ArgumentMatchers.any(), ArgumentMatchers.any());

      ArgumentCaptor<HttpRequest.BodyPublisher> bodyCaptor =
          ArgumentCaptor.forClass(HttpRequest.BodyPublisher.class);
      Mockito.verify(httpRequestBuilderMock, Mockito.times(1)).POST(bodyCaptor.capture());

      /* Verify that the payload of the HTTP is a proper callback message*/
      /* Too simple but there is no other way to access the internal payload. IT tested */
      Assertions.assertEquals(dummyJson.length(), bodyCaptor.getValue().contentLength());

      ArgumentCaptor<CallbackMessage> objectMapperCaptor =
          ArgumentCaptor.forClass(CallbackMessage.class);
      Mockito.verify(objectMapper, Mockito.times(1))
          .writeValueAsString(objectMapperCaptor.capture());

      /* Verify that the payload passed to object mapper is the expected one*/
      CallbackMessage capturedMessage = objectMapperCaptor.getValue();
      CallbackMessage expectedMessage =
          new CallbackMessage(
              DUMMY_ID_NAME,
              DUMMY_KEY_NAME,
              map.getWrappedMap(),
              scheduleEventMetadata.getTriggerTime(),
              scheduleEventMetadata.getNotificationAttempt());

      Assertions.assertEquals(expectedMessage, capturedMessage);

      /* Verify AMPQ is not used */
      Mockito.verifyNoInteractions(rabbitTemplate);
    }
  }

  @Test
  void httpCallbackSendFailureCasesKO() throws IOException, InterruptedException {
    long callbackTimeout = 1000L;
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, objectMapper, DUMMY_EXCHANGE_NAME, callbackTimeout);

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

    String dummyJson = "{\"test\":\"value\"}";
    Mockito.when(objectMapper.writeValueAsString(ArgumentMatchers.any())).thenReturn(dummyJson);

    HttpRequest.Builder httpRequestBuilderMock = Mockito.mock(HttpRequest.Builder.class);
    Mockito.when(httpRequestBuilderMock.POST(ArgumentMatchers.any()))
        .thenReturn(httpRequestBuilderMock);
    Mockito.when(httpRequestBuilderMock.timeout(ArgumentMatchers.any()))
        .thenReturn(httpRequestBuilderMock);
    Mockito.when(
            httpRequestBuilderMock.header(
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(httpRequestBuilderMock);
    HttpRequest mockRequest = Mockito.mock(HttpRequest.class);
    Mockito.when(httpRequestBuilderMock.build()).thenReturn(mockRequest);
    try (MockedStatic<HttpRequest> httpRequestStaticMock = Mockito.mockStatic(HttpRequest.class)) {
      httpRequestStaticMock
          .when(() -> HttpRequest.newBuilder(ArgumentMatchers.any(URI.class)))
          .thenReturn(httpRequestBuilderMock);

      HttpResponse<Object> mockResponse = Mockito.mock(HttpResponse.class);
      Mockito.when(httpClient.send(ArgumentMatchers.eq(mockRequest), ArgumentMatchers.any()))
          .thenReturn(mockResponse);

      /* Test non reeschedulable exception is launched by 4XX errors */
      Mockito.when(mockResponse.statusCode()).thenReturn(403);
      RuntimeException exception =
          Assertions.assertThrows(
              RuntimeException.class,
              () -> {
                callbackService.executeCallback(schedulerJobData, map);
              });

      /* Ensure that the threw exception is NOT a recoverable one */
      Assertions.assertNull(
          AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));

      Mockito.when(mockResponse.statusCode()).thenReturn(500);
      exception =
          Assertions.assertThrows(
              RuntimeException.class,
              () -> {
                callbackService.executeCallback(schedulerJobData, map);
              });

      /* Ensure that the threw exception is a recoverable one */
      Assertions.assertNotNull(
          AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));

      /* Ensure IOExceptions are recoverable */
      Mockito.when(httpClient.send(ArgumentMatchers.any(), ArgumentMatchers.any()))
          .thenThrow(new IOException());
      exception =
          Assertions.assertThrows(
              RuntimeException.class,
              () -> {
                callbackService.executeCallback(schedulerJobData, map);
              });

      /* Ensure that the threw exception is a recoverable one */
      Assertions.assertNotNull(
          AnnotationUtils.findAnnotation(exception.getClass(), Reeschedulable.class));

      /* Verify AMPQ is not used */
      Mockito.verifyNoInteractions(rabbitTemplate);
    }
  }

  @Test
  void amqpCallbackSendFailureCasesKO() {
    CallbackService callbackService =
        new CallbackService(rabbitTemplate, objectMapper, DUMMY_EXCHANGE_NAME, 1000);

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

    Mockito.verifyNoInteractions(httpClient);
  }
}
