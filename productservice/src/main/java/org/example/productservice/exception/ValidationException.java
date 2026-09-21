package org.example.productservice.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class ValidationException extends RuntimeException{
    private final List<String> errors;

    public ValidationException(List<String> errors){
        super(errors.size()==1
                ?errors.get(0)
                :String.join("; ",errors));
        this.errors=errors;
    }

    public ValidationException(String error){
        this(List.of(error));
    }
}