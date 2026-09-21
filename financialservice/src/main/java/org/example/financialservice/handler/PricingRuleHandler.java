package org.example.financialservice.handler;

import org.example.financialservice.dto.PricingRuleRequest;
import org.example.financialservice.dto.UpdatePricingRuleStatusRequest;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.service.PricingRuleService;
import org.example.financialservice.util.RequestValidationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class PricingRuleHandler{
    private final PricingRuleService service;
    private final RequestValidationUtil validation;

    public PricingRuleHandler(
            PricingRuleService service,
            RequestValidationUtil validation
    ){
        this.service=service;
        this.validation=validation;
    }

    public Mono<ServerResponse> create(ServerRequest request){
        return request.bodyToMono(PricingRuleRequest.class)
                .map(validation::validate)
                .flatMap(service::create)
                .flatMap(response->
                        ServerResponse.status(HttpStatus.CREATED)
                                .bodyValue(response)
                );
    }

    public Mono<ServerResponse> findAll(ServerRequest request){
        return ServerResponse.ok()
                .body(service.findAll(),Object.class);
    }

    public Mono<ServerResponse> findById(ServerRequest request){
        return service.findById(ruleId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> update(ServerRequest request){
        Long ruleId=ruleId(request);

        return request.bodyToMono(PricingRuleRequest.class)
                .map(validation::validate)
                .flatMap(body->service.update(ruleId,body))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> updateStatus(
            ServerRequest request
    ){
        Long ruleId=ruleId(request);

        return request
                .bodyToMono(UpdatePricingRuleStatusRequest.class)
                .map(validation::validate)
                .flatMap(body->
                        service.updateStatus(ruleId,body)
                )
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    public Mono<ServerResponse> archive(ServerRequest request){
        return service.archive(ruleId(request))
                .flatMap(response->
                        ServerResponse.ok().bodyValue(response)
                );
    }

    private Long ruleId(ServerRequest request){
        try{
            return Long.valueOf(
                    request.pathVariable("ruleId")
            );
        }catch(NumberFormatException exception){
            throw new ValidationException(
                    "Invalid pricing rule ID"
            );
        }
    }
}