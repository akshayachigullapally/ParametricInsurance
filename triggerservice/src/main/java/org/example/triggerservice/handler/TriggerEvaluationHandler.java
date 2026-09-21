package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.EvaluateTriggerRequest;
import org.example.triggerservice.exception.ForbiddenException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.TriggerEvaluationService;
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
public class TriggerEvaluationHandler{
    private static final Set<String> VIEW_ROLES=Set.of(
            "ROLE_ADMIN","ROLE_UNDERWRITER","ROLE_CLAIMS_OFFICER"
    );

    private final TriggerEvaluationService triggerEvaluationService;
    private final RequestValidationUtil validationUtil;

    public Mono<ServerResponse> getAll(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .thenMany(triggerEvaluationService.getAll())
                .collectList()
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> getById(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"evaluationId"))
                .flatMap(triggerEvaluationService::getById)
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> getByDefinitionId(ServerRequest request){
        return requireAnyRole(VIEW_ROLES)
                .then(parseId(request,"definitionId"))
                .flatMapMany(
                        triggerEvaluationService::getByDefinitionId)
                .collectList()
                .flatMap(result->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
    }

    public Mono<ServerResponse> evaluate(ServerRequest request){
        // support both definition-based evaluation and internal product-based override evaluation
        String path=request.path();
        if(path.contains("/internal/triggers/products/")){
            // internal product evaluation: expect productId path variable and admin-provided payload
            return requireAdmin()
                    .then(requireUserId())
                    .zipWith(parseId(request,"productId"))
                    .flatMap(tuple->request
                            .bodyToMono(org.example.productservice.dto.ProductTriggerSyncRequest.class)
                            .flatMap(body->{
                                // map product payload to a temporary trigger definition flow inside triggerEvaluationService
                                return triggerEvaluationService.evaluateProductOverride(
                                        tuple.getT2(),
                                        body,
                                        tuple.getT1());
                            }))
                    .flatMap(result->ServerResponse.status(201)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(result));
        }

        return requireAdmin()
                .then(requireUserId())
                .zipWith(parseId(request,"definitionId"))
                .flatMap(tuple->request
                        .bodyToMono(EvaluateTriggerRequest.class)
                        .defaultIfEmpty(new EvaluateTriggerRequest())
                        .flatMap(validationUtil::validate)
                        .flatMap(body->
                                triggerEvaluationService.evaluate(
                                        tuple.getT2(),
                                        body,
                                        tuple.getT1())))
                .flatMap(result->ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(result));
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