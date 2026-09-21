package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.MessageResponse;
import org.example.triggerservice.dto.ObservationRejectRequest;
import org.example.triggerservice.dto.ObservationResponse;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.ObservationValidationService;
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
public class ObservationValidationHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN","ROLE_UNDERWRITER","ROLE_CLAIMS_OFFICER"
    );

    private final ObservationValidationService validationService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getReceived(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(validationService.getReceivedObservations())
                .collectList()
                .flatMap(observations->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(observations));
    }

    public Mono<ServerResponse> validate(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"observationId"))
                .flatMap(tuple->validationService.validateObservation(
                        tuple.getT2(), tuple.getT1()))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> reject(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"observationId"))
                .flatMap(tuple->request
                        .bodyToMono(ObservationRejectRequest.class)
                        .switchIfEmpty(Mono.error(new ValidationException(
                                "Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->validationService.rejectObservation(
                                tuple.getT2(), body.getReason(), tuple.getT1())))
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> validateAll(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .flatMap(userId->validationService.validateAllReceived(userId))
                .flatMap(count->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(MessageResponse.builder()
                                .message("Validated "+count+" observations")
                                .build()));
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

    private Mono<Long> parseId(ServerRequest request, String variable){
        try{
            long id=Long.parseLong(request.pathVariable(variable));
            if(id<=0) return Mono.error(new ValidationException(
                    variable+" must be greater than zero"));
            return Mono.just(id);
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException("Invalid "+variable));
        }
    }
}
