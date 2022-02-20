package com.pablintino.schedulerservice.exceptions;

import com.pablintino.services.commons.exceptions.ValidationHttpServiceException;

public class SchedulerValidationException extends ValidationHttpServiceException {
  public SchedulerValidationException(String message) {
    super(message);
  }

  public SchedulerValidationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
