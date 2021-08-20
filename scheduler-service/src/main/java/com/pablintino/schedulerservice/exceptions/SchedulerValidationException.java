package com.pablintino.schedulerservice.exceptions;

import com.pablintino.services.commons.exceptions.GenericHttpServiceException;
import org.springframework.http.HttpStatus;

public class SchedulerValidationException extends GenericHttpServiceException {
    public SchedulerValidationException(String message){
        super(message, HttpStatus.BAD_REQUEST.value());
    }

    public SchedulerValidationException(String message, Throwable throwable){
        super(message, HttpStatus.BAD_REQUEST.value(), throwable);
    }
}
