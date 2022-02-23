package com.pablintino.schedulerservice.exceptions;

public class SchedulerValidationException extends Exception {
  public SchedulerValidationException(String message) {
    super(message);
  }

  public SchedulerValidationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
