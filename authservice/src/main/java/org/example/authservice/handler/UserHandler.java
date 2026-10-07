package org.example.authservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.authservice.dto.*;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.UserProfileService;
import org.example.authservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserHandler{
    private final UserProfileService userProfileService;
    private final Validator validator;

    public Mono<ServerResponse> saveMyProfile(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(userId->readAndValidate(request,UpdateProfileRequest.class)
                        .flatMap(body->userProfileService.saveProfile(userId,body)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getMyProfile(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(userProfileService::getMyProfile)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getAllUsers(ServerRequest request){
        String status=request.queryParam("status").orElse(null);
        String segment=request.queryParam("segment").orElse(null);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(userProfileService.getAllUsers(status,segment),UserResponse.class);
    }

    public Mono<ServerResponse> getUser(ServerRequest request){
        return parseUserId(request)
                .flatMap(userProfileService::getUser)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request){
        return parseUserId(request)
                .flatMap(userId->readAndValidate(request,UpdateUserStatusRequest.class)
                        .flatMap(body->userProfileService.updateStatus(userId,body)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private <T> Mono<T> readAndValidate(ServerRequest request,Class<T> type){
        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(
                        new ValidationException("Request body is required")
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

    private Mono<Long> parseUserId(ServerRequest request){
        try{
            Long userId=Long.valueOf(request.pathVariable("userId"));

            if(userId<=0){
                return Mono.error(
                        new ValidationException("User id must be positive")
                );
            }

            return Mono.just(userId);
        }catch(NumberFormatException e){
            return Mono.error(
                    new ValidationException("Invalid user id")
            );
        }
    }
}