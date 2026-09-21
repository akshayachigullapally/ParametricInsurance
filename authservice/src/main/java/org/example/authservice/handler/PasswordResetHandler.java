package org.example.authservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.authservice.dto.*;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.PasswordResetService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PasswordResetHandler{
    private final PasswordResetService passwordResetService;
    private final Validator validator;

    public Mono<ServerResponse> forgotPassword(ServerRequest request){
        return readAndValidate(request,ForgotPasswordRequest.class)
                .flatMap(body->passwordResetService.generateToken(body.getEmail()))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> resetPassword(ServerRequest request){
        return readAndValidate(request,ResetPasswordRequest.class)
                .flatMap(body->passwordResetService.resetPassword(
                        body.getToken(),body.getNewPassword()))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private <T> Mono<T> readAndValidate(ServerRequest request,Class<T> type){
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(
                        new ValidationException(List.of("Request body is required"))
                ))
                .flatMap(this::validate);
    }

    private <T> Mono<T> validate(T body){
        List<String> errors=validator.validate(body).stream()
                .map(this::message)
                .toList();

        return errors.isEmpty()
                ?Mono.just(body)
                :Mono.error(new ValidationException(errors));
    }

    private String message(ConstraintViolation<?> violation){
        String field=violation.getPropertyPath().toString();
        return field.isBlank()
                ?violation.getMessage()
                :field+": "+violation.getMessage();
    }
}
