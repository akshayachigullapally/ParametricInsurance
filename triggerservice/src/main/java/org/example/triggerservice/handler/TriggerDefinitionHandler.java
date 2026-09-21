package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.MessageResponse;
import org.example.triggerservice.dto.TriggerDefinitionRequest;
import org.example.triggerservice.dto.UpdateTriggerDefinitionStatusRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.TriggerDefinitionService;
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
public class TriggerDefinitionHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN",
            "ROLE_UNDERWRITER",
            "ROLE_CLAIMS_OFFICER"
    );

    private final TriggerDefinitionService triggerDefinitionService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(triggerDefinitionService.getAll())
                .collectList()
                .flatMap(definitions->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(definitions));
    }

    public Mono<ServerResponse> getActive(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(triggerDefinitionService.getActive())
                .collectList()
                .flatMap(definitions->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(definitions));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"definitionId"))
                .flatMap(triggerDefinitionService::getById)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getByPerilId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"perilId"))
                .flatMapMany(triggerDefinitionService::getByPerilId)
                .collectList()
                .flatMap(definitions->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(definitions));
    }

    public Mono<ServerResponse> getByZoneId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"zoneId"))
                .flatMapMany(triggerDefinitionService::getByLocationZoneId)
                .collectList()
                .flatMap(definitions->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(definitions));
    }

    public Mono<ServerResponse> getByProductId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"productId"))
                .flatMapMany(triggerDefinitionService::getByProductId)
                .collectList()
                .flatMap(definitions->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(definitions));
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .flatMap(userId->request.bodyToMono(TriggerDefinitionRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ValidationException("Request body is required")
                        ))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->
                                triggerDefinitionService.create(body,userId)
                        ))
                .flatMap(response->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> update(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"definitionId"))
                .flatMap(tuple->
                        request.bodyToMono(TriggerDefinitionRequest.class)
                                .switchIfEmpty(Mono.error(
                                        new ValidationException(
                                                "Request body is required"
                                        )
                                ))
                                .flatMap(validationUtil::validate)
                                .flatMap(body->
                                        triggerDefinitionService.update(
                                                tuple.getT2(),
                                                body,
                                                tuple.getT1()
                                        )
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"definitionId"))
                .flatMap(tuple->
                        request.bodyToMono(
                                        UpdateTriggerDefinitionStatusRequest.class
                                )
                                .switchIfEmpty(Mono.error(
                                        new ValidationException(
                                                "Request body is required"
                                        )
                                ))
                                .flatMap(validationUtil::validate)
                                .flatMap(body->
                                        triggerDefinitionService.updateStatus(
                                                tuple.getT2(),
                                                body.getStatus(),
                                                tuple.getT1()
                                        )
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> archive(ServerRequest request){
        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"definitionId"))
                .flatMap(tuple->
                        triggerDefinitionService.archive(
                                tuple.getT2(),
                                tuple.getT1()
                        )
                )
                .then(ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(MessageResponse.builder()
                                .message("Trigger definition archived successfully")
                                .build()));
    }

    private Mono<Long> requireUserId(){
        return SecurityUtil.getUserId()
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "Authenticated user ID is unavailable"
                )));
    }

    private Mono<Void> requireAdmin(){
        return requireAnyRole(Set.of("ROLE_ADMIN"));
    }

    private Mono<Void> requireAnyRole(Set<String> roles){
        return SecurityUtil.getRole()
                .filter(roles::contains)
                .switchIfEmpty(Mono.error(new ForbiddenException(
                        "You are not authorized to perform this operation"
                )))
                .then();
    }

    private Mono<Long> parseId(ServerRequest request,String variable){
        try{
            long id=Long.parseLong(request.pathVariable(variable));

            if(id<=0){
                return Mono.error(new ValidationException(
                        variable+" must be greater than zero"
                ));
            }

            return Mono.just(id);
        }catch(NumberFormatException exception){
            return Mono.error(new ValidationException("Invalid "+variable));
        }
    }
}