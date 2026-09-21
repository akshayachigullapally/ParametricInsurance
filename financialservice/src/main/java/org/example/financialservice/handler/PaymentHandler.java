package org.example.financialservice.handler;

import org.example.financialservice.dto.PaymentActionRequest;
import org.example.financialservice.dto.PaymentRequest;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.service.PaymentService;
import org.example.financialservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PaymentHandler{
    private final PaymentService service;
    private final RequestValidationUtil validation;

    public PaymentHandler(
            PaymentService service,
            RequestValidationUtil validation
    ){
        this.service=service;
        this.validation=validation;
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return request.bodyToMono(PaymentRequest.class)
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
        return service.findById(paymentId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> findAll(ServerRequest request){
        return ServerResponse.ok()
                .body(service.findAll(),Object.class);
    }

    public Mono<ServerResponse> success(ServerRequest request){
        return request.bodyToMono(PaymentActionRequest.class)
                .defaultIfEmpty(
                        PaymentActionRequest.builder().build()
                )
                .flatMap(body->service.success(
                        paymentId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> fail(ServerRequest request){
        return request.bodyToMono(PaymentActionRequest.class)
                .defaultIfEmpty(
                        PaymentActionRequest.builder().build()
                )
                .flatMap(body->service.fail(
                        paymentId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> refund(ServerRequest request){
        return service.refund(paymentId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> retry(ServerRequest request){
        return service.retry(paymentId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> policyStatus(ServerRequest request){
        return service.policyStatus(policyId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    private Long paymentId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("paymentId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException(
                    "Invalid payment ID"
            );
        }
    }

    private Long policyId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("policyId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException(
                    "Invalid policy ID"
            );
        }
    }
}