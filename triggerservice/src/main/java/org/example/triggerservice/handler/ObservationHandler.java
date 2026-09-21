package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.CreateObservationRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.ObservationService;
import org.example.triggerservice.util.RequestValidationUtil;
import org.example.triggerservice.util.SecurityUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class ObservationHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER"
    );

    private final ObservationService observationService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(observationService.getAll())
                .collectList()
                .flatMap(observations->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observations));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"observationId"))
                .flatMap(observationService::getById)
                .flatMap(observation->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observation));
    }

    public Mono<ServerResponse> getByZoneId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"zoneId"))
                .flatMapMany(observationService::getByZoneId)
                .collectList()
                .flatMap(observations->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observations));
    }

    public Mono<ServerResponse> getByMeasurementType(
            ServerRequest request
    ){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(observationService.getByMeasurementType(
                        request.pathVariable("measurementType")))
                .collectList()
                .flatMap(observations->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observations));
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .flatMap(userId->request
                        .bodyToMono(CreateObservationRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ValidationException(
                                        "Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->
                                observationService.create(body,userId)))
                .flatMap(observation->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observation));
    }

    private Mono<Long> requireUserId(){
        return SecurityUtil.getUserId()
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "Authenticated user ID is unavailable")));
    }

    private Mono<Void> requireAdmin(){
        return requireAnyRole(Set.of("ROLE_ADMIN"));
    }

    private Mono<Void> requireAnyRole(Set<String> roles){
        return SecurityUtil.getRole()
                .filter(roles::contains)
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "You are not authorized to perform this operation")))
                .then();
    }

    private Mono<Long> parseId(
            ServerRequest request,
            String variable
    ){
        try{
            long id=Long.parseLong(request.pathVariable(variable));

            if(id<=0){
                return Mono.error(new ValidationException(
                        variable+" must be greater than zero"));
            }

            return Mono.just(id);
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException(
                    "Invalid "+variable));
        }
    }
}