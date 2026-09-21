package org.example.financialservice.util;

import java.util.stream.Collectors;
import org.example.financialservice.exception.ValidationException;
import org.springframework.stereotype.Component;
import jakarta.validation.Validator;

@Component
public class RequestValidationUtil{
    private final Validator validator;

    public RequestValidationUtil(Validator validator){
        this.validator=validator;
    }

    public <T> T validate(T request){
        var violations=validator.validate(request);

        if(!violations.isEmpty()){
            String message=violations.stream()
                    .map(violation->
                            violation.getPropertyPath()+" "+
                                    violation.getMessage()
                    )
                    .collect(Collectors.joining(", "));

            throw new ValidationException(message);
        }

        return request;
    }
}