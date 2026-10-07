package org.example.authservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.authservice.dto.*;
import org.example.authservice.exception.AuthenticationException;
import org.example.authservice.exception.ValidationException;
import org.example.authservice.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthHandler{
    private final AuthService authService;
    private final Validator validator;

    public Mono<ServerResponse> register(ServerRequest request){
        return readAndValidate(request,RegisterRequest.class)
                .flatMap(body->authService.register(body,getDeviceName(request),getIpAddress(request)))
                .flatMap(response->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> login(ServerRequest request){
        return readAndValidate(request,LoginRequest.class)
                .flatMap(body->authService.login(body,getDeviceName(request),getIpAddress(request)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> refresh(ServerRequest request){
        return readAndValidate(request,RefreshTokenRequest.class)
                .flatMap(body->authService.refresh(body,getDeviceName(request),getIpAddress(request)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> logout(ServerRequest request){
        return currentUserId(request)
                .flatMap(userId->readAndValidate(request,RefreshTokenRequest.class)
                        .flatMap(body->authService.logout(userId,body)))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> me(ServerRequest request){
        return currentUserId(request)
                .flatMap(authService::getCurrentUser)
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
                .map(this::validationMessage)
                .toList();

        return errors.isEmpty()
                ?Mono.just(body)
                :Mono.error(new ValidationException(errors));
    }

    private String validationMessage(ConstraintViolation<?> violation){
        String field=violation.getPropertyPath().toString();
        return field.isBlank()
                ?violation.getMessage()
                :field+": "+violation.getMessage();
    }

    private Mono<Long> currentUserId(ServerRequest request){
        return request.principal()
                .cast(Authentication.class)
                .map(Authentication::getPrincipal)
                .flatMap(this::convertUserId)
                .switchIfEmpty(Mono.error(
                        new AuthenticationException("Authentication is required")
                ));
    }

    private Mono<Long> convertUserId(Object principal){
        if(principal instanceof Long userId) return Mono.just(userId);

        if(principal instanceof Number value){
            return Mono.just(value.longValue());
        }

        if(principal instanceof String value){
            try{
                return Mono.just(Long.valueOf(value));
            }catch(NumberFormatException e){
                return Mono.error(
                        new AuthenticationException("Invalid authenticated user id")
                );
            }
        }

        return Mono.error(
                new AuthenticationException("Invalid authenticated principal")
        );
    }

    private Mono<Long> parseSessionId(ServerRequest request){
        try{
            Long sessionId=Long.valueOf(request.pathVariable("sessionId"));

            if(sessionId<=0){
                return Mono.error(
                        new ValidationException(List.of("Session id must be positive"))
                );
            }

            return Mono.just(sessionId);
        }catch(NumberFormatException e){
            return Mono.error(
                    new ValidationException(List.of("Invalid session id"))
            );
        }
    }

    private String getDeviceName(ServerRequest request){
        String userAgent=request.headers().firstHeader("User-Agent");

        if(userAgent==null||userAgent.isBlank()) return "Unknown device";

        return userAgent.length()>150
                ?userAgent.substring(0,150)
                :userAgent;
    }

    private String getIpAddress(ServerRequest request){
        String forwardedFor=request.headers().firstHeader("X-Forwarded-For");

        if(forwardedFor!=null&&!forwardedFor.isBlank()){
            String ip=forwardedFor.split(",")[0].trim();
            return limitIp(ip);
        }

        String realIp=request.headers().firstHeader("X-Real-IP");

        if(realIp!=null&&!realIp.isBlank()){
            return limitIp(realIp.trim());
        }

        return request.remoteAddress()
                .map(address->limitIp(address.getAddress().getHostAddress()))
                .orElse("Unknown");
    }

    private String limitIp(String ip){
        return ip.length()>100?ip.substring(0,100):ip;
    }
}