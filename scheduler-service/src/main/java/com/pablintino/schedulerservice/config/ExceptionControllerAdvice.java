package com.pablintino.schedulerservice.config;

import com.pablintino.schedulerservice.exceptions.SchedulerValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ExceptionControllerAdvice  {

    @ExceptionHandler(SchedulerValidationException.class)
    public ResponseEntity<String> handleWhateverException(SchedulerValidationException ex, HttpServletRequest httpServletRequest) {
        return new ResponseEntity(createCommonMap(ex, httpServletRequest), HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> createCommonMap(Exception ex, HttpServletRequest httpServletRequest){
        Map<String, Object> detailsMap = new HashMap<>();

        detailsMap.put("timestamp", LocalDateTime.now().toInstant(ZoneOffset.UTC).getEpochSecond());
        detailsMap.put("error", ex.getMessage());
        detailsMap.put("exception", ex.getClass().getName());
        detailsMap.put("path", httpServletRequest.getRequestURI());
        return detailsMap;
    }
}
