package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.amqp.AmqpCallbackMessage;
import com.pablintino.schedulerservice.exceptions.RemoteUnreachableException;
import com.pablintino.schedulerservice.models.CallbackType;

import com.pablintino.schedulerservice.models.ScheduleEventMetadata;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.quartz.JobDataMap;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.util.HashMap;

@Service
public class CallbackService implements ICallbackService {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public CallbackService(
            RabbitTemplate rabbitTemplate,
            @Value("${com.pablintino.scheduler.amqp.exchange-name}") String exchangeName
    ){
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
    }

    @Override
    public void executeCallback(SchedulerJobData jobData, JobDataMap jobDataMap, ScheduleEventMetadata scheduleEventMetadata) {
        if (jobData.getType() == CallbackType.AMQP) {
            executeAmqpCallback(jobData, jobDataMap, scheduleEventMetadata);
        } else {
            // TODO Implement remaining callback types
            throw new UnsupportedOperationException("Callback type not implemented");
        }
    }

    private void executeAmqpCallback(SchedulerJobData schedulerJobData, JobDataMap jobDataMap, ScheduleEventMetadata scheduleEventMetadata) {
        AmqpCallbackMessage message = new AmqpCallbackMessage(
                schedulerJobData.getTaskId(),
                schedulerJobData.getKey(),
                /* Convert to a serializable one (assumes values are serializable too) */
                jobDataMap.getWrappedMap(),
                scheduleEventMetadata.getTriggerTime().toEpochMilli(),
                scheduleEventMetadata.getAttempt()
        );

        try {
            rabbitTemplate.convertAndSend(exchangeName, schedulerJobData.getKey(), message);
        }catch (AmqpException ex){
            Throwable rootCause = ExceptionUtils.getRootCause(ex);
            if(rootCause != null && !rootCause.equals(ex) && (
                    IOException.class.isAssignableFrom(rootCause.getClass()) ||
                    ConnectException.class.isAssignableFrom(rootCause.getClass())
            )){
                throw new RemoteUnreachableException(ex);
            }
            throw ex;
        }
    }
}
