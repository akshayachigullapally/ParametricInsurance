package org.example.notificationservice.util;

import jakarta.validation.Validator;
import org.example.notificationservice.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RequestValidationUtil{
private final Validator validator;
public RequestValidationUtil(Validator validator){this.validator=validator;}
public <T>T validate(T request){var errors=validator.validate(request);if(!errors.isEmpty())throw new ValidationException(errors.stream().map(e->e.getPropertyPath()+" "+e.getMessage()).collect(Collectors.joining(", ")));return request;}
}