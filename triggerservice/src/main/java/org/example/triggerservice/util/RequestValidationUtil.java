package org.example.triggerservice.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.exception.ValidationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RequestValidationUtil{
    private final Validator validator;

    public <T> Mono<T> validate(T request){
        Set<ConstraintViolation<T>> violations=validator.validate(request);
        if(violations.isEmpty()) return Mono.just(request);

        String message=violations.stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining(", "));

        return Mono.error(new ValidationException(message));
    }
}