package org.example.financialservice.handler;

import org.example.financialservice.dto.CreateQuoteRequest;
import org.example.financialservice.dto.InternalQuoteConsumeRequest;
import org.example.financialservice.dto.InternalQuoteValidateRequest;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.service.QuoteService;
import org.example.financialservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class QuoteHandler{
    private final QuoteService service;
    private final RequestValidationUtil validation;

    public QuoteHandler(
            QuoteService service,
            RequestValidationUtil validation
    ){
        this.service=service;
        this.validation=validation;
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return request.bodyToMono(CreateQuoteRequest.class)
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
        return service.findMine(quoteId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> accept(ServerRequest request){
        return service.accept(quoteId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> cancel(ServerRequest request){
        return service.cancel(quoteId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> priceInternal(ServerRequest request){
        return request.bodyToMono(org.example.financialservice.dto.InternalPricingRequest.class)
                .map(validation::validate)
                .flatMap(service::priceInternal)
                .flatMap(response->ServerResponse.ok().bodyValue(response));
    }

    public Mono<ServerResponse> validateInternal(
            ServerRequest request
    ){
        return request
                .bodyToMono(InternalQuoteValidateRequest.class)
                .map(validation::validate)
                .flatMap(body->service.validateInternal(
                        quoteId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> consumeInternal(
            ServerRequest request
    ){
        return request
                .bodyToMono(InternalQuoteConsumeRequest.class)
                .map(validation::validate)
                .flatMap(body->service.consume(
                        quoteId(request),
                        body
                ))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    private Long quoteId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("quoteId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException("Invalid quote ID");
        }
    }
}