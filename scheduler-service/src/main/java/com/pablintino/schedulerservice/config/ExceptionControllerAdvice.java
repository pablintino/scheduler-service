package com.pablintino.schedulerservice.config;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.pablintino.services.commons.exceptions.GenericHttpServiceException;
import com.pablintino.services.commons.exceptions.ValidationHttpServiceException;
import com.pablintino.services.commons.mappers.ErrorBodyMapper;
import com.pablintino.services.commons.responses.HttpErrorBody;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ExceptionControllerAdvice {

  private final ErrorBodyMapper errorBodyMapper;

  public ExceptionControllerAdvice(
      @Value("${com.pablintino.scheduler.service.debug:false}") boolean debugMode) {
    this.errorBodyMapper = new ErrorBodyMapper(debugMode);
  }

  @ExceptionHandler(GenericHttpServiceException.class)
  public ResponseEntity<HttpErrorBody> handleGenericHttpServiceException(
      GenericHttpServiceException ex, HttpServletRequest httpServletRequest) {
    return new ResponseEntity<>(
        errorBodyMapper.mapFromException(ex, httpServletRequest),
        HttpStatus.resolve(ex.getStatus()));
  }

  @ExceptionHandler(ValidationHttpServiceException.class)
  public ResponseEntity<HttpErrorBody> handleValidationHttpServiceException(
      ValidationHttpServiceException ex, HttpServletRequest httpServletRequest) {
    return new ResponseEntity<>(
        errorBodyMapper.mapFromValidationException(ex, httpServletRequest),
        HttpStatus.resolve(ex.getStatus()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<HttpErrorBody> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex, HttpServletRequest httpServletRequest) {
    Map<String, String> errors = new HashMap<>();
    if (ex.getCause() instanceof MismatchedInputException) {
      String fieldName =
          ((MismatchedInputException) ex.getCause())
              .getPath().stream()
                  .map(JsonMappingException.Reference::getFieldName)
                  .collect(Collectors.joining("."));
      errors.put(fieldName, ExceptionUtils.getRootCause(ex).getMessage());
    }

    return new ResponseEntity<>(
        errorBodyMapper.mapFromValidationException(ex, httpServletRequest, 400, errors),
        HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<HttpErrorBody> handleValidationExceptions(
      MethodArgumentNotValidException ex, HttpServletRequest httpServletRequest) {
    Map<String, String> errors =
        ex.getBindingResult().getAllErrors().stream()
            .collect(
                Collectors.toMap(
                    k -> ((FieldError) k).getField(),
                    DefaultMessageSourceResolvable::getDefaultMessage));

    return new ResponseEntity<>(
        errorBodyMapper.mapFromValidationException(
            ex, httpServletRequest, HttpStatus.BAD_REQUEST.value(), errors),
        HttpStatus.BAD_REQUEST);
  }
}
