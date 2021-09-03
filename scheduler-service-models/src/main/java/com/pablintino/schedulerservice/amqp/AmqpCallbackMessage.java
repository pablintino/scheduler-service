package com.pablintino.schedulerservice.amqp;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Getter
@ToString
@EqualsAndHashCode
public class AmqpCallbackMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private final Map<String, Object> dataMap; //NOSONAR Forced to be serializable as values are Java primitives
    private final String id;
    private final String key;
    private final long triggerTime;
    private final int notificationAttempt;

    public AmqpCallbackMessage(String id, String key, Map<String, Object> dataMap, long triggerTime, int notificationAttempt) {
        this.id = id;
        this.key = key;
        this.dataMap = dataMap != null ? dataMap : new HashMap<>();
        this.triggerTime = triggerTime;
        this.notificationAttempt = notificationAttempt;
    }
}
