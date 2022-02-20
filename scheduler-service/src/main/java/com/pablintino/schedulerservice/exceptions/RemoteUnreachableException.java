package com.pablintino.schedulerservice.exceptions;

import com.pablintino.schedulerservice.quartz.annotations.Reeschedulable;

@Reeschedulable
public class RemoteUnreachableException extends RuntimeException {

  public RemoteUnreachableException(Throwable throwable) {
    super(throwable);
  }
}
