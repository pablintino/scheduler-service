package com.pablintino.schedulerservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

@Slf4j
@Configuration
public class AmqpConfiguration {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final var rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(producerJackson2MessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public Jackson2JsonMessageConverter producerJackson2MessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    DirectExchange exchange(@Value("${com.pablintino.scheduler.amqp.exchange-name}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @EventListener({ContextRefreshedEvent.class})
    void contextRefreshedEvent() {
        Map<String, Object> serverProperties = rabbitTemplate.execute((channel) -> channel.getConnection().getServerProperties());
        log.debug("Auto boot RabbitMQ call performed. Server details {}", serverProperties);
    }
}
