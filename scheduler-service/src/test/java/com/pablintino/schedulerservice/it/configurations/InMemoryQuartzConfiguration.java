package com.pablintino.schedulerservice.it.configurations;

import com.pablintino.schedulerservice.config.scheduler.SchedulerJobFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class InMemoryQuartzConfiguration {
    @Bean
    SchedulerJobFactory schedulerJobFactory(){
        return new SchedulerJobFactory();
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(SchedulerJobFactory jobFactory) throws IOException {
        Properties props = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream stream = loader.getResourceAsStream("test-quartz-in-memory.properties");
        props.load(stream);

        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setOverwriteExistingJobs(true);
        factory.setQuartzProperties(props);
        factory.setJobFactory(jobFactory);
        return factory;
    }
}
