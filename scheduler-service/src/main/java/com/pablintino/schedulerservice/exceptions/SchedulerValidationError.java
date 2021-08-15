package com.pablintino.schedulerservice.exceptions;

public class SchedulerValidationError extends SchedulingException{
    public SchedulerValidationError(String message){
        super(message);
    }

    public SchedulerValidationError(String message, Throwable throwable){
        super(message, throwable);
    }
}
