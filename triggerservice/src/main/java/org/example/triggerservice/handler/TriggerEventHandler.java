package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.TriggerEventDecisionRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.TriggerEventService;
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
public class TriggerEventHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN","ROLE_UNDERWRITER","ROLE_CLAIMS_OFFICER"
    );

    private final TriggerEventService triggerEventService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(triggerEventService.getAll())
                .collectList()
                .flatMap(result->ok(result));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"eventId"))
                .flatMap(triggerEventService::getById)
                .flatMap(this::ok);
    }

    public Mono<ServerResponse> getByStatus(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(triggerEventService.getByStatus(
                        request.pathVariable("status")))
                .collectList()
                .flatMap(result->ok(result));
    }

    public Mono<ServerResponse> getByDefinitionId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"definitionId"))
                .flatMapMany(triggerEventService::getByDefinitionId)
                .collectList()
                .flatMap(result->ok(result));
    }

    public Mono<ServerResponse> getByProductId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"productId"))
                .flatMapMany(triggerEventService::getByProductId)
                .collectList()
                .flatMap(result->ok(result));
    }

    public Mono<ServerResponse> getByZoneId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"zoneId"))
                .flatMapMany(triggerEventService::getByZoneId)
                .collectList()
                .flatMap(result->ok(result));
    }

    public Mono<ServerResponse> startValidation(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"eventId"))
                .flatMap(tuple->triggerEventService.startValidation(
                        tuple.getT2(),tuple.getT1()))
                .flatMap(this::ok);
    }

    public Mono<ServerResponse> verify(ServerRequest request){
        return processDecision(request,true);
    }

    public Mono<ServerResponse> reject(ServerRequest request){
        return processDecision(request,false);
    }

    public Mono<ServerResponse> process(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"eventId"))
                .flatMap(tuple->request
                        .bodyToMono(TriggerEventDecisionRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ValidationException(
                                        "Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->triggerEventService.processEvent(
                                tuple.getT2(),
                                body.getReviewNotes(),
                                tuple.getT1())))
                .flatMap(this::ok);
    }

    public Mono<ServerResponse> markProcessed(ServerRequest request){
        return parseId(request,"eventId")
                .flatMap(triggerEventService::markEventProcessed)
                .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> processDecision(
            ServerRequest request,
            boolean verify
    ){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"eventId"))
                .flatMap(tuple->request
                        .bodyToMono(TriggerEventDecisionRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ValidationException(
                                        "Request body is required")))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->verify
                                ?triggerEventService.verify(
                                tuple.getT2(),
                                body.getReviewNotes(),
                                tuple.getT1())
                                :triggerEventService.reject(
                                tuple.getT2(),
                                body.getReviewNotes(),
                                tuple.getT1())))
                .flatMap(this::ok);
    }

    private Mono<ServerResponse> ok(Object body){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
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

    private Mono<Long> parseId(ServerRequest request,String variable){
        try{
            long id=Long.parseLong(request.pathVariable(variable));
            if(id<=0) return Mono.error(new ValidationException(
                    variable+" must be greater than zero"));
            return Mono.just(id);
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException(
                    "Invalid "+variable));
        }
    }
}