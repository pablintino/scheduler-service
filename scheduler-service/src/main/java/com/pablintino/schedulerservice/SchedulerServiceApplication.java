package com.pablintino.schedulerservice;

import com.pablintino.schedulerservice.config.CommonConfiguration;
import com.pablintino.schedulerservice.config.QuatzConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(value = {CommonConfiguration.class, QuatzConfiguration.class})
public class SchedulerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SchedulerServiceApplication.class, args);
	}

}
