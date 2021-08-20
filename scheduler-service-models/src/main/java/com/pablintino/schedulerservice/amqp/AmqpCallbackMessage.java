package com.pablintino.schedulerservice.amqp;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Getter
@ToString
@EqualsAndHashCode
public class AmqpCallbackMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ToString.Exclude
    private final Map<String, Object> dataMap;
    private final String id;
    private final String key;

    public AmqpCallbackMessage(String id, String key, Map<String, Object> dataMap){
        this.id = id;
        this.key = key;
        this.dataMap = dataMap != null ? dataMap : new HashMap<>();
    }
}
