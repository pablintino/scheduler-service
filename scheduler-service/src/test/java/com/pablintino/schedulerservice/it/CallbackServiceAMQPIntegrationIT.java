package com.pablintino.schedulerservice.it;

import com.pablintino.schedulerservice.dtos.AmqpCallbackMessage;
import com.pablintino.schedulerservice.it.configurations.AmqpTestIntegrationConfiguration;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.ICallbackService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@Import(AmqpTestIntegrationConfiguration.class)
class CallbackServiceAMQPIntegrationIT {
	@Autowired
	private ICallbackService callbackService;

	@Autowired
	private DirectExchange exchange;

	private BlockingQueue<AmqpCallbackMessage> messageQueue = new LinkedBlockingQueue<>();

	@Test
	void simpleSendOK() throws InterruptedException {
		JobDataMap map = new JobDataMap();
		map.put("test-key", "test");
		SchedulerJobData schedulerJobData = new SchedulerJobData("test", AmqpTestIntegrationConfiguration.QUEUE_KEY, null, CallbackType.AMQP);
		callbackService.executeCallback(schedulerJobData, map);

		AmqpCallbackMessage message = messageQueue.poll(10, TimeUnit.SECONDS);
		Assertions.assertNotNull(message);
	}

	@RabbitListener(queues={AmqpTestIntegrationConfiguration.QUEUE_KEY})
	public void queueListener(AmqpCallbackMessage callbackMessage){
		try {
			messageQueue.put(callbackMessage);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
