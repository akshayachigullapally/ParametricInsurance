package org.example.productservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.productservice.dto.CreateCustomPolicyRequest;
import org.example.productservice.dto.CreatePolicyRequest;
import org.example.productservice.dto.PolicyDecisionRequest;
import org.example.productservice.dto.PolicyResponse;
import org.example.productservice.dto.PolicyReviewHistoryResponse;
import org.example.productservice.dto.PolicyStatusHistoryResponse;
import org.example.productservice.exception.ValidationException;
import org.example.productservice.service.PolicyService;
import org.example.productservice.util.SecurityUtil;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PolicyHandler{
    private final PolicyService policyService;
    private final org.example.productservice.service.ZoneUnderwritingService zoneUnderwritingService;
    private final Validator validator;

    public Mono<ServerResponse> createPolicy(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->
                        readAndValidate(request, CreatePolicyRequest.class)
                                .flatMap(body->
                                        policyService.createPolicy(customerId,body)
                                )
                )
                .flatMap(response->ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> createCustomPolicy(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->
                        readAndValidate(request, CreateCustomPolicyRequest.class)
                                .flatMap(body->
                                        policyService.createCustomPolicy(customerId,body)
                                )
                )
                .flatMap(response->ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getActiveZones(ServerRequest request){
        return policyService.getActiveZones()
                .flatMap(zones -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(zones));
    }

    public Mono<ServerResponse> getZoneExposure(ServerRequest request){
        return zoneUnderwritingService.exposureReport()
                .flatMap(report -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(report));
    }

    public Mono<ServerResponse> getMyPolicies(ServerRequest request){
        String status=request.queryParam("status").orElse(null);

        return SecurityUtil.getUserId(request)
                .flatMap(customerId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                policyService.getMyPolicies(customerId,status),
                                PolicyResponse.class
                        ));
    }

    public Mono<ServerResponse> getMyPolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyId->
                        SecurityUtil.getUserId(request)
                                .flatMap(customerId->
                                        policyService.getMyPolicy(customerId,policyId)
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> cancelPolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyId->
                        SecurityUtil.getUserId(request)
                                .flatMap(customerId->
                                        policyService.cancelPolicy(customerId,policyId)
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getReviewHistory(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(reviewerId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                policyService.getReviewHistory(reviewerId),
                                PolicyReviewHistoryResponse.class
                        ));
    }

    public Mono<ServerResponse> getPendingPolicies(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        policyService.getPendingPolicies(),
                        PolicyResponse.class
                );
    }

    public Mono<ServerResponse> getAllPolicies(ServerRequest request){
        String status=request.queryParam("status").orElse(null);
        Long customerId=request.queryParam("customerId")
                .map(Long::parseLong)
                .orElse(null);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        policyService.getAllPolicies(status,customerId),
                        PolicyResponse.class
                );
    }

    public Mono<ServerResponse> getPolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyService::getPolicy)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> approvePolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyId->
                        SecurityUtil.getUserId(request)
                                .flatMap(reviewerId->
                                        policyService.approvePolicy(reviewerId,policyId)
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> rejectPolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyId->
                        SecurityUtil.getUserId(request)
                                .flatMap(reviewerId->
                                        readAndValidate(request,PolicyDecisionRequest.class)
                                                .flatMap(body->
                                                        policyService.rejectPolicy(
                                                                reviewerId,
                                                                policyId,
                                                                body
                                                        )
                                                )
                                )
                )
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    private <T> Mono<T> readAndValidate(
            ServerRequest request,
            Class<T> type){

        return request.bodyToMono(type)
                .switchIfEmpty(Mono.error(
                        new ValidationException("Request body is required")
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

    private String validationMessage(
            ConstraintViolation<?> violation){

        String field=violation.getPropertyPath().toString();

        return field.isBlank()
                ?violation.getMessage()
                :field+": "+violation.getMessage();
    }

    private Mono<Long> parsePolicyId(ServerRequest request){
        try{
            Long policyId=Long.valueOf(
                    request.pathVariable("policyId")
            );

            if(policyId<=0){
                return Mono.error(
                        new ValidationException("Policy id must be positive")
                );
            }

            return Mono.just(policyId);
        }catch(NumberFormatException e){
            return Mono.error(
                    new ValidationException("Invalid policy id")
            );
        }
    }

    public Mono<ServerResponse> getInternalPolicy(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyService::getInternalPolicy)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getPolicyHistory(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                policyService.getPolicyHistory(policyId),
                                PolicyStatusHistoryResponse.class
                        ));
    }

    public Mono<ServerResponse> getPolicySnapshot(ServerRequest request){
        return parsePolicyId(request)
                .flatMap(policyService::getPolicySnapshot)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }
}