package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.ProductTriggerSyncRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.ProductTriggerSyncService;
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
    private final ProductTriggerSyncService productTriggerSyncService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"definitionId"))
                .flatMap(triggerDefinitionService::getById)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> syncProductDefinition(ServerRequest request){
        return parseId(request,"productId")
                .flatMap(productId->request.bodyToMono(
                                ProductTriggerSyncRequest.class)
                        .switchIfEmpty(Mono.error(
                                new ValidationException(
                                        "Request body is required"
                                )))
                        .flatMap(validationUtil::validate)
                        .flatMap(body->productTriggerSyncService
                                .syncProductDefinition(productId,body)))
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
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