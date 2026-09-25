package org.example.claimsservice.handler;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.claimsservice.dto.*;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.service.ClaimService;
import org.example.claimsservice.util.SecurityUtil;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ClaimHandler{
    private final ClaimService claimService;
    private final Validator validator;

    public Mono<ServerResponse> createClaim(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(customerId->readAndValidate(
                        request,CreateClaimRequest.class
                ).flatMap(body->claimService.createClaim(customerId,body)))
                .flatMap(response->ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    public Mono<ServerResponse> getMyClaims(ServerRequest request){
        String status=request.queryParam("status").orElse(null);

        return SecurityUtil.getUserId(request)
                .flatMap(customerId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                claimService.getMyClaims(customerId,status),
                                ClaimResponse.class
                        ));
    }

    public Mono<ServerResponse> getMyClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(customerId->
                                claimService.getMyClaim(customerId,claimId)
                        ))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> cancelClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(customerId->
                                claimService.cancelClaim(customerId,claimId)
                        ))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> getReviewHistory(ServerRequest request){
        return SecurityUtil.getUserId(request)
                .flatMap(officerId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(claimService.getReviewHistory(officerId),
                                org.example.claimsservice.dto.ClaimReviewHistoryResponse.class));
    }

    public Mono<ServerResponse> getSubmittedClaims(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(claimService.getSubmittedClaims(),ClaimResponse.class);
    }

    public Mono<ServerResponse> getManualClaims(ServerRequest request){
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(claimService.getManualClaims(),ClaimResponse.class);
    }

    public Mono<ServerResponse> getManualClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimService::getManualClaim)
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> getAllClaims(ServerRequest request){
        String status=request.queryParam("status").orElse(null);
        Long customerId=request.queryParam("customerId")
                .map(Long::parseLong)
                .orElse(null);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(claimService.getAllClaims(status,customerId),ClaimResponse.class);
    }

    public Mono<ServerResponse> getClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimService::getClaim)
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> getInternalClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimService::getInternalClaim)
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

        public Mono<ServerResponse> createInternalClaim(ServerRequest request){
                return request.bodyToMono(CreateClaimRequest.class)
                                .switchIfEmpty(Mono.error(new org.example.claimsservice.exception.ValidationException("Request body is required")))
                        .flatMap(body->claimService.createInternalClaim(body))
                                .flatMap(response->ServerResponse.status(HttpStatus.CREATED)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .bodyValue(response));
        }

        public Mono<ServerResponse> createClaimsFromTriggerEvent(ServerRequest request){
                return request.bodyToMono(org.example.claimsservice.dto.ClaimCreationRequest.class)
                        .switchIfEmpty(Mono.error(new org.example.claimsservice.exception.ValidationException("Request body is required")))
                        .flatMap(claimService::createClaimsFromTriggerEvent)
                        .flatMap(response->ServerResponse.status(HttpStatus.CREATED)
                                .contentType(MediaType.APPLICATION_JSON).bodyValue(response));
        }

    public Mono<ServerResponse> startReview(ServerRequest request){
        return reviewerAction(
                request,
                (reviewerId,claimId)->claimService.startReview(
                        reviewerId,claimId
                )
        );
    }

    public Mono<ServerResponse> approveClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(reviewerId->readAndValidate(
                                request,ApproveClaimRequest.class
                        ).flatMap(body->claimService.approveClaim(
                                reviewerId,claimId,body
                        ))))
                .flatMap(response->claimService
                        .requestPayout(response.getId())
                        .thenReturn(response))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> rejectClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(reviewerId->readAndValidate(
                                request,RejectClaimRequest.class
                        ).flatMap(body->claimService.rejectClaim(
                                reviewerId,claimId,body
                        ))))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> settleClaim(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(officerId->
                                readBodyOptional(request,SettleClaimRequest.class)
                                        .flatMap(body->claimService.settleClaim(
                                                officerId,claimId,body
                                        ))))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> getMyClaimTimeline(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(customerId->ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        claimService.getClaimTimeline(customerId,claimId),
                                        ClaimTimelineResponse.class
                                )));
    }

    public Mono<ServerResponse> getClaimTimeline(ServerRequest request){
        return parseClaimId(request)
                .flatMap(claimId->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                                claimService.getClaimTimelineAdmin(claimId),
                                ClaimTimelineResponse.class
                        ));
    }

    private Mono<ServerResponse> reviewerAction(
            ServerRequest request,
            java.util.function.BiFunction<
                    Long,Long,Mono<ClaimResponse>> action){

        return parseClaimId(request)
                .flatMap(claimId->SecurityUtil.getUserId(request)
                        .flatMap(reviewerId->action.apply(reviewerId,claimId)))
                .flatMap(response->ServerResponse.ok().bodyValue(response));
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
                .map(this::message)
                .toList();

        return errors.isEmpty()
                ?Mono.just(body)
                :Mono.error(new ValidationException(errors));
    }

    private <T> Mono<T> readBodyOptional(
            ServerRequest request,
            Class<T> type
    ){
        return request.bodyToMono(type);
    }

    private String message(ConstraintViolation<?> violation){
        String field=violation.getPropertyPath().toString();
        return field.isBlank()
                ?violation.getMessage()
                :field+": "+violation.getMessage();
    }

    private Mono<Long> parseClaimId(ServerRequest request){
        try{
            Long id=Long.valueOf(request.pathVariable("claimId"));

            return id>0
                    ?Mono.just(id)
                    :Mono.error(new ValidationException(
                    "Claim id must be positive"
            ));
        }catch(NumberFormatException e){
            return Mono.error(
                    new ValidationException("Invalid claim id")
            );
        }
    }
}