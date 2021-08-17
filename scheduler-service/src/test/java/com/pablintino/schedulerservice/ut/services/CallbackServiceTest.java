package com.pablintino.schedulerservice.ut.services;

import com.pablintino.schedulerservice.dtos.AmqpCallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.CallbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
public class CallbackServiceTest {

    private final String DUMMY_EXCHANGE_NAME = "exchange-name";
    private final String DUMMY_KEY_NAME = "test-key";
    private final String DUMMY_ID_NAME = "test-id";

    @Mock
    RabbitTemplate rabbitTemplate;

    @Test
    void simpleAMQPCallbackSendOK() {
        CallbackService callbackService = new CallbackService(rabbitTemplate, DUMMY_EXCHANGE_NAME);

        /** Prepare the dummy data to be sent */
        JobDataMap map = new JobDataMap();
        map.put("test-key", "test");
        SchedulerJobData schedulerJobData = new SchedulerJobData(DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP);

        /** Call the service to enqueue the AMPQ message */
        callbackService.executeCallback(schedulerJobData, map);

        /** Verify the expected enqueued data */
        AmqpCallbackMessage expectedMessage = new AmqpCallbackMessage(DUMMY_ID_NAME, DUMMY_KEY_NAME, map.getWrappedMap());
        Mockito.verify(rabbitTemplate, Mockito.times(1)).convertAndSend(DUMMY_EXCHANGE_NAME, DUMMY_KEY_NAME, expectedMessage);
    }
}