package com.pablintino.schedulerservice.configurations;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;

public class AmqpTestIntegrationConfiguration {

  public static final String QUEUE_KEY = "svc.schedules.test-queue";

  @Bean
  Binding binding(Queue queue, DirectExchange exchange) {
    return BindingBuilder.bind(queue).to(exchange).withQueueName();
  }

  @Bean
  Queue queue() {
    return new Queue(QUEUE_KEY, false);
  }
}
