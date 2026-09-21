package org.example.financialservice.handler;

import org.example.financialservice.dto.CreatePayoutRequest;
import org.example.financialservice.dto.PayoutActionRequest;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.service.PayoutService;
import org.example.financialservice.util.RequestValidationUtil;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PayoutHandler{
    private final PayoutService service;
    private final RequestValidationUtil validation;

    public PayoutHandler(
            PayoutService service,
            RequestValidationUtil validation
    ){
        this.service=service;
        this.validation=validation;
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return SecurityUtil.requireClaimsOfficerOrAdmin()
                .then(createInternal(request));
    }

    public Mono<ServerResponse> createInternal(ServerRequest request){
        return request.bodyToMono(CreatePayoutRequest.class)
                .map(validation::validate)
                .flatMap(service::create)
                .flatMap(response->
                        ServerResponse.status(HttpStatus.CREATED)
                                .bodyValue(response)
                );
    }

    public Mono<ServerResponse> findMine(ServerRequest request){
        return ServerResponse.ok()
                .body(service.findMine(),Object.class);
    }

    public Mono<ServerResponse> findById(ServerRequest request){
        return service.findById(payoutId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> findAll(ServerRequest request){
        return ServerResponse.ok()
                .body(service.findAll(),Object.class);
    }

    public Mono<ServerResponse> findByClaim(ServerRequest request){
        return ServerResponse.ok()
                .body(service.findByClaim(claimId(request)),
                        Object.class);
    }

    public Mono<ServerResponse> process(ServerRequest request){
        return actionBody(request)
                .flatMap(body->service.process(
                        payoutId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> success(ServerRequest request){
        return actionBody(request)
                .flatMap(body->service.success(
                        payoutId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> fail(ServerRequest request){
        return actionBody(request)
                .flatMap(body->service.fail(
                        payoutId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> retry(ServerRequest request){
        return actionBody(request)
                .flatMap(body->service.retry(
                        payoutId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    private Mono<PayoutActionRequest> actionBody(
            ServerRequest request
    ){
        return request
                .bodyToMono(PayoutActionRequest.class)
                .defaultIfEmpty(
                        PayoutActionRequest.builder().build()
                );
    }

    private Long payoutId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("payoutId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException(
                    "Invalid payout ID"
            );
        }
    }

    private Long claimId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("claimId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException(
                    "Invalid claim ID"
            );
        }
    }
}