package com.pablintino.schedulerservice.config;

import com.pablintino.services.commons.exceptions.GenericHttpServiceException;
import com.pablintino.services.commons.mappers.ErrorBodyMapper;
import com.pablintino.services.commons.responses.HttpErrorBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ExceptionControllerAdvice {

    private final ErrorBodyMapper errorBodyMapper;

    public ExceptionControllerAdvice(@Value("${com.pablintino.scheduler.service.debug:false}") boolean debugMode){
        this.errorBodyMapper = new ErrorBodyMapper(debugMode);
    }

    @ExceptionHandler(GenericHttpServiceException.class)
    public ResponseEntity<HttpErrorBody> handleGenericHttpServiceException(GenericHttpServiceException ex, HttpServletRequest httpServletRequest) {
        HttpErrorBody errorBody = errorBodyMapper.mapFromException(ex);
        errorBody.setPath(httpServletRequest.getRequestURI());
        return new ResponseEntity<>(errorBody, HttpStatus.resolve(errorBody.getStatus()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HttpErrorBody> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest httpServletRequest) {
        Map<String, String> errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .collect(Collectors.toMap(k -> ((FieldError)k).getField(), DefaultMessageSourceResolvable::getDefaultMessage));

        return new ResponseEntity<>(errorBodyMapper.mapFromValidationException(ex, httpServletRequest, HttpStatus.BAD_REQUEST.value(), errors), HttpStatus.BAD_REQUEST);
    }
}
