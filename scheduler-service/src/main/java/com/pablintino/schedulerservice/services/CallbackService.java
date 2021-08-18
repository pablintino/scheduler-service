package com.pablintino.schedulerservice.services;

import com.pablintino.schedulerservice.amqp.AmqpCallbackMessage;
import com.pablintino.schedulerservice.models.CallbackType;
import com.pablintino.schedulerservice.models.SchedulerJobData;
import org.quartz.JobDataMap;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    public void executeCallback(SchedulerJobData jobData, JobDataMap jobDataMap) {
        if (jobData.type() == CallbackType.AMQP) {
            executeAmqpCallback(jobData, jobDataMap);
        } else {
            // TODO Implement remaining callback types
            throw new UnsupportedOperationException("Callback type not implemented");
        }
    }

    private void  executeAmqpCallback(SchedulerJobData schedulerJobData, JobDataMap jobDataMap) {
        AmqpCallbackMessage message = new AmqpCallbackMessage(
                schedulerJobData.taskId(),
                schedulerJobData.key(),
                jobDataMap.getWrappedMap()
        );
        rabbitTemplate.convertAndSend(exchangeName, schedulerJobData.key(), message);
    }
}
