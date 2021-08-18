package com.pablintino.schedulerservice.config;


import com.pablintino.schedulerservice.config.scheduler.SchedulerJobFactory;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
public class QuartzConfiguration {

    @Bean
    SchedulerJobFactory schedulerJobFactory(){
        return new SchedulerJobFactory();
    }

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource,
                                                     SchedulerJobFactory jobFactory,
                                                     QuartzProperties quartzProperties) {
        Properties props = new Properties();
        props.putAll(quartzProperties.getProperties());
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setOverwriteExistingJobs(true);
        factory.setDataSource(dataSource);
        factory.setQuartzProperties(props);
        factory.setJobFactory(jobFactory);
        return factory;
    }
}
