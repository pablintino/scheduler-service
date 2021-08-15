package com.pablintino.schedulerservice.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfiguration {

    @Bean
    DirectExchange exchange(@Value("${com.pablintino.scheduler.amqp.exchange-name}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }
}
