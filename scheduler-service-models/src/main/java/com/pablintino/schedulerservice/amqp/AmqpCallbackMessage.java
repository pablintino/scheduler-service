package com.pablintino.schedulerservice.amqp;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class AmqpCallbackMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private Map<String, Object> dataMap; //NOSONAR Forced to be serializable as values are Java primitives
    private String id;
    private String key;
    private long triggerTime;
    private int notificationAttempt;


    public AmqpCallbackMessage() {
    }

    public AmqpCallbackMessage(String id, String key, Map<String, Object> dataMap, long triggerTime, int notificationAttempt) {
        this.id = id;
        this.key = key;
        this.dataMap = dataMap != null ? dataMap : new HashMap<>();
        this.triggerTime = triggerTime;
        this.notificationAttempt = notificationAttempt;
    }
}
