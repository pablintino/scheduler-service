package com.pablintino.schedulerservice.exceptions;

public class CallbackHandleException extends RuntimeException {
  public CallbackHandleException(String message) {
    super(message);
  }

  public CallbackHandleException(Throwable throwable) {
    super(throwable);
  }
}
