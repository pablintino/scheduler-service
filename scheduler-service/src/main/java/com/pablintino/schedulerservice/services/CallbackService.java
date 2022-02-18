package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.callback.CallbackMessage;
import com.pablintino.schedulerservice.exceptions.RemoteUnreachableException;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class CallbackService implements ICallbackService {

  private final RabbitTemplate rabbitTemplate;
  private final String exchangeName;
  private final WebClient webClient;
  private final long httpCallbackTimeout;

  public CallbackService(
      RabbitTemplate rabbitTemplate,
      WebClient.Builder webClientBuilder,
      @Value("${com.pablintino.scheduler.amqp.exchange-name}") String exchangeName,
      @Value("${com.pablintino.scheduler.http.callback-timeout:1000}") long httpCallbackTimeout) {
    this.rabbitTemplate = rabbitTemplate;
    this.exchangeName = exchangeName;
    this.httpCallbackTimeout = httpCallbackTimeout;
    this.webClient =
        webClientBuilder
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public void executeCallback(SchedulerJobData jobData, Map<String, Object> taskDataMap) {
    if (jobData.getType() == CallbackType.AMQP) {
      executeAmqpCallback(jobData, taskDataMap);
    } else {
      executeHttpCallback(jobData, taskDataMap);
    }
  }

  private void executeAmqpCallback(
      SchedulerJobData schedulerJobData, Map<String, Object> taskDataMap) {
    CallbackMessage message = buildCallbackMessage(schedulerJobData, taskDataMap);

    try {
      rabbitTemplate.convertAndSend(exchangeName, schedulerJobData.getKey(), message);
    } catch (AmqpException ex) {
      Throwable rootCause = ExceptionUtils.getRootCause(ex);
      if (rootCause != null
          && !rootCause.equals(ex)
          && (IOException.class.isAssignableFrom(rootCause.getClass())
              || ConnectException.class.isAssignableFrom(rootCause.getClass()))) {
        throw new RemoteUnreachableException(ex);
      }
      throw ex;
    }
  }

  private void executeHttpCallback(
      SchedulerJobData schedulerJobData, Map<String, Object> taskDataMap) {
    CallbackMessage message = buildCallbackMessage(schedulerJobData, taskDataMap);

    try {
      webClient
          .post()
          .uri(schedulerJobData.getCallbackUrl())
          .body(Mono.just(message), CallbackMessage.class)
          .retrieve()
          .bodyToMono(Void.class)
          .timeout(Duration.of(httpCallbackTimeout, ChronoUnit.MILLIS))
          .block();
    } catch (WebClientResponseException ex) {
      if (ex.getStatusCode().is5xxServerError()) {
        /* Maybe something gone wrong... Another call may succeed */
        throw new RemoteUnreachableException(ex);
      }
      /* Another call won't fix a 400/401/403/404... Don't reschedule... */
      throw ex;
    } catch (Exception ex) {
      Throwable root = ExceptionUtils.getRootCause(ex);
      if (root != null) {
        Class rootType = root.getClass();
        if (ConnectException.class.isAssignableFrom(rootType)
            || TimeoutException.class.isAssignableFrom(rootType)
            || UnknownHostException.class.isAssignableFrom(rootType)
            || PrematureCloseException.class.isAssignableFrom(rootType)) {
          /* Connection refused, timeout, DNS issues, TCP connection suddenly closed. Maybe transient/temporal */
          throw new RemoteUnreachableException(ex);
        }
      }
      throw ex;
    }
  }

  private static CallbackMessage buildCallbackMessage(
      SchedulerJobData schedulerJobData, Map<String, Object> taskDataMap) {
    return new CallbackMessage(
        schedulerJobData.getTaskId(),
        schedulerJobData.getKey(),
        taskDataMap,
        schedulerJobData.getMetadata().getLastTriggerTime(),
        schedulerJobData.getMetadata().getNotificationAttempt());
  }
}
