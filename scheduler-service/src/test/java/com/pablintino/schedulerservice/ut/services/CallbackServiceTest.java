package com.pablintino.schedulerservice.ut.services;

import com.pablintino.schedulerservice.amqp.AmqpCallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import com.pablintino.schedulerservice.services.CallbackService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

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
        ScheduleEventMetadata scheduleEventMetadata = new ScheduleEventMetadata();
        scheduleEventMetadata.setAttempt(0);
        scheduleEventMetadata.setTriggerTime(Instant.now());
        SchedulerJobData schedulerJobData = new SchedulerJobData(DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP);

        /** Call the service to enqueue the AMPQ message */
        callbackService.executeCallback(schedulerJobData, map, scheduleEventMetadata);

        /** Verify the expected enqueued data */
        AmqpCallbackMessage expectedMessage = new AmqpCallbackMessage(DUMMY_ID_NAME, DUMMY_KEY_NAME, map.getWrappedMap(), scheduleEventMetadata.getTriggerTime().toEpochMilli(), scheduleEventMetadata.getAttempt());
        Mockito.verify(rabbitTemplate, Mockito.times(1)).convertAndSend(DUMMY_EXCHANGE_NAME, DUMMY_KEY_NAME, expectedMessage);
    }

    @Test
    void simpleAMQPCallbackSendFailSendKO() {
        CallbackService callbackService = new CallbackService(rabbitTemplate, DUMMY_EXCHANGE_NAME);

        /** Prepare the dummy data to be sent */
        JobDataMap map = new JobDataMap();
        map.put("test-key", "test");
        ScheduleEventMetadata scheduleEventMetadata = new ScheduleEventMetadata();
        scheduleEventMetadata.setAttempt(0);
        scheduleEventMetadata.setTriggerTime(Instant.now());
        SchedulerJobData schedulerJobData = new SchedulerJobData(DUMMY_ID_NAME, DUMMY_KEY_NAME, null, CallbackType.AMQP);

        /** Simulate an exception thrown while sending the callback message */
        Mockito.doThrow(new AmqpException("test exception")).when(rabbitTemplate).convertAndSend(Mockito.anyString(), Mockito.anyString(), Mockito.any(AmqpCallbackMessage.class));

        /** Assert that the exception is not replaced or masquerade with any other one */
        Assertions.assertThrows(AmqpException.class, () -> {
            callbackService.executeCallback(schedulerJobData, map, scheduleEventMetadata);
        });
    }
}
