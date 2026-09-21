package org.example.authservice.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class ValidationException extends RuntimeException{
    private final List<String> errors;

    public ValidationException(List<String> errors){
        super("Validation failed");
        this.errors=errors;
    }

    public ValidationException(String error){
        this(List.of(error));
    }
}