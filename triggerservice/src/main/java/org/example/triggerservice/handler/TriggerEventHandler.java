package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.TriggerEventService;
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

    public Mono<ServerResponse> markProcessed(ServerRequest request){
        return parseId(request,"eventId")
                .flatMap(triggerEventService::markEventProcessed)
                .then(ServerResponse.ok().build());
    }

    private Mono<ServerResponse> ok(Object body){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body);
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