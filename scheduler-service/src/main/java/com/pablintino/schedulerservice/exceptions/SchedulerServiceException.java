package com.pablintino.schedulerservice.exceptions;

public class SchedulerServiceException extends RuntimeException {
  public SchedulerServiceException(String message) {
    super(message);
  }

  public SchedulerServiceException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
