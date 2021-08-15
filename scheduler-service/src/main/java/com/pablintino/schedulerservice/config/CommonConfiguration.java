package com.pablintino.schedulerservice.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "com.pablintino.schedulerservice.services")
public class CommonConfiguration {
}
