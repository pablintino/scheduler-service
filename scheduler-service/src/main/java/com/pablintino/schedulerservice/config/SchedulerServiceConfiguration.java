package com.pablintino.schedulerservice.config;

import com.pablintino.services.commons.exceptions.config.ServiceCommonsExceptionsConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ComponentScan(
    basePackages = {
      "com.pablintino.schedulerservice.services",
      "com.pablintino.schedulerservice.rest",
      "com.pablintino.schedulerservice.quartz"
    },
    basePackageClasses = SchedulerServiceConfiguration.class)
@Import(ServiceCommonsExceptionsConfiguration.class)
public class SchedulerServiceConfiguration {}
