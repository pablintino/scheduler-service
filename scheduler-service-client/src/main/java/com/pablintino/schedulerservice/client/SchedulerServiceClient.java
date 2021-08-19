package com.pablintino.schedulerservice.client;

import com.pablintino.schedulerservice.amqp.AmqpCallbackMessage;
import com.pablintino.schedulerservice.dtos.CallbackDescriptorDto;
import com.pablintino.schedulerservice.dtos.CallbackMethodTypeDto;
import com.pablintino.schedulerservice.dtos.ScheduleRequestDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SchedulerServiceClient implements ISchedulerServiceClient {

    private final Map<String, IScheduleCallback> callbackMap = new HashMap<>();
    private final RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;
    private final AmqpAdmin rabbitAdmin;
    private final static String LISTENER_ID = "scheduler-client-listener";
    private final WebClient schedulerClient;
    private final long clientTimeout;
    private final String exchange;


    public SchedulerServiceClient(WebClient.Builder webClientBuilder, RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry,
                                  RabbitAdmin rabbitAdmin,
                                  @Value("${com.pablintino.scheduler.client.url}") String baseUrl,
                                  @Value("${com.pablintino.scheduler.client.exchange-name}") String exchange,
                                  @Value("${com.pablintino.scheduler.client.timeout:10000}") long clientTimeout) {
        this.rabbitListenerEndpointRegistry = rabbitListenerEndpointRegistry;
        this.clientTimeout = clientTimeout;
        this.rabbitAdmin = rabbitAdmin;
        this.exchange = exchange;
        this.schedulerClient = webClientBuilder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
    }

    @Override
    public void scheduleTask(String key, String id, ZonedDateTime triggerTime, Map<String, Object> data) {
        Assert.hasLength(key, "key cannot be null");
        Assert.notNull(id, "id cannot be null or empty");
        Assert.notNull(id, "triggerTime cannot be null");

        /** Prepare the request body */
        ScheduleRequestDto request = new ScheduleRequestDto();
        request.setTaskData(data);
        request.setTaskKey(key);
        request.setTaskIdentifier(id);
        request.setTriggerTime(triggerTime);
        CallbackDescriptorDto callbackDescriptor = new CallbackDescriptorDto();
        callbackDescriptor.setType(CallbackMethodTypeDto.AMQP);
        request.setCallbackDescriptor(callbackDescriptor);

        schedulerClient.post()
                .uri("/schedules")
                .body(Mono.just(request), ScheduleRequestDto.class)
                .retrieve()
                .onStatus(HttpStatus::isError, response -> {
                    log.error("Error");
                    // TODO Retrieve error body and use it in the exception
                    return Mono.error(new ScheduleServiceClientException("Schedule request rejected"));
                })
                .bodyToMono(Void.class)
                .timeout(Duration.of(clientTimeout, ChronoUnit.MILLIS)).block();
    }

    @Override
    public void registerCallback(String key, IScheduleCallback callback) {
        if(callbackMap.containsKey(key)){
            throw new ScheduleServiceClientException("Key  " + key + " was already registered");
        }

        Queue queue = new Queue(key);
        if(rabbitAdmin.getQueueInfo(key) == null){
            rabbitAdmin.declareQueue(queue);
        }

        rabbitAdmin.declareBinding(BindingBuilder.bind(queue).to(new DirectExchange(exchange)).withQueueName());

        AbstractMessageListenerContainer container = ((AbstractMessageListenerContainer)rabbitListenerEndpointRegistry.getListenerContainer(LISTENER_ID));
        if(Arrays.stream(container.getQueueNames()).noneMatch(qn -> qn.equals(key))){
            container.addQueueNames(key);
            container.stop();
            container.start();
        }

        callbackMap.put(key, callback);
    }

    @RabbitListener(id = LISTENER_ID, concurrency = "10")
    public void queueListener(AmqpCallbackMessage callbackMessage){
        if(callbackMap.containsKey(callbackMessage.getKey())){
            callbackMap.get(callbackMessage.getKey())
                    .callback(callbackMessage.getId(), callbackMessage.getKey(), callbackMessage.getDataMap());
        }
    }
}