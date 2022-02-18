package com.pablintino.schedulerservice.exceptions;

import com.pablintino.services.commons.exceptions.GenericHttpServiceException;

public class SchedulingException extends GenericHttpServiceException {
  public SchedulingException(String message) {
    super(message);
  }

  public SchedulingException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
