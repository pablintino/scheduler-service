package com.pablintino.schedulerservice.exceptions;

public class SchedulingException extends Exception{
    public SchedulingException(String message){
        super(message);
    }
    public SchedulingException(String message, Throwable throwable){
        super(message, throwable);
    }

}
