package com.pablintino.schedulerservice.callback;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class CallbackMessage implements Serializable {

  private static final long serialVersionUID = 1L;

  @ToString.Exclude
  private Map<String, Object>
      dataMap; // NOSONAR Forced to be serializable as values are Java primitives

  private String id;

  private String key;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private Instant triggerTime;

  private long notificationAttempt;

  public CallbackMessage() {}

  public CallbackMessage(
      String id,
      String key,
      Map<String, Object> dataMap,
      Instant triggerTime,
      long notificationAttempt) {
    this.id = id;
    this.key = key;
    this.dataMap = dataMap != null ? dataMap : new HashMap<>();
    this.triggerTime = triggerTime;
    this.notificationAttempt = notificationAttempt;
  }
}
