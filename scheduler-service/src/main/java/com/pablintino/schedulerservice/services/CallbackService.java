package com.pablintino.schedulerservice.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.exceptions.CallbackHandleException;
import com.pablintino.schedulerservice.exceptions.RemoteUnreachableException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class CallbackService implements ICallbackService {

  private final RabbitTemplate rabbitTemplate;
  private final String exchangeName;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final long httpCallbackTimeout;

  public CallbackService(
      RabbitTemplate rabbitTemplate,
      ObjectMapper objectMapper,
      @Value("${com.pablintino.scheduler.amqp.exchange-name}") String exchangeName,
      @Value("${com.pablintino.scheduler.http.callback-timeout:1000}") long httpCallbackTimeout) {
    this.rabbitTemplate = rabbitTemplate;
    this.objectMapper = objectMapper;
    this.exchangeName = exchangeName;
    this.httpCallbackTimeout = httpCallbackTimeout;
    this.httpClient = HttpClient.newBuilder().build();
  }

  @Override
  public void executeCallback(SchedulerJobData jobData, Object taskData) {
    if (jobData.getType() == CallbackType.AMQP) {
      executeAmqpCallback(jobData, taskData);
    } else {
      executeHttpCallback(jobData, taskData);
    }
  }

  private void executeAmqpCallback(SchedulerJobData schedulerJobData, Object taskData) {
    CallbackMessage message = buildCallbackMessage(schedulerJobData, taskData);

    try {
      rabbitTemplate.convertAndSend(exchangeName, schedulerJobData.getKey(), message);
    } catch (AmqpException ex) {
      Throwable rootCause = ExceptionUtils.getRootCause(ex);
      if (rootCause != null
          && !rootCause.equals(ex)
          && (IOException.class.isAssignableFrom(rootCause.getClass()))) {
        throw new RemoteUnreachableException(ex);
      }
      throw ex;
    }
  }

  private void executeHttpCallback(SchedulerJobData schedulerJobData, Object taskData) {
    CallbackMessage message = buildCallbackMessage(schedulerJobData, taskData);

    try {
      HttpRequest httpRequest =
          HttpRequest.newBuilder(URI.create(schedulerJobData.getCallbackUrl()))
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .timeout(Duration.of(httpCallbackTimeout, ChronoUnit.MILLIS))
              .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
              .build();

      HttpResponse<String> httpResponse =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

      int statusCode = httpResponse.statusCode();
      if (statusCode / 100 == 5) {
        /* 5XX: Maybe something gone wrong... Another call may succeed */
        throw new RemoteUnreachableException("Remote cannot handle request. Code " + statusCode);
      }
      if (statusCode / 100 != 2) {
        /* Another call won't fix a 400/401/403/404... Don't reschedule... */
        throw new CallbackHandleException("HTTP request failed with response code " + statusCode);
      }
    } catch (JsonProcessingException ex) {
      /* Should never occur as the payload has been previously serialized... */
      log.error("Error serializing HTTP callback request");
      throw new CallbackHandleException(ex);
    } catch (IOException ex) {
      /* Connection issues, timeout, peer reset connections, etc. */
      throw new RemoteUnreachableException(ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new CallbackHandleException(ex);
    }
  }

  private static CallbackMessage buildCallbackMessage(
      SchedulerJobData schedulerJobData, Object taskData) {
    return new CallbackMessage(
        schedulerJobData.getTaskId(),
        schedulerJobData.getKey(),
        taskData,
        schedulerJobData.getMetadata().getTriggerTime(),
        schedulerJobData.getMetadata().getNotificationAttempt());
  }
}
