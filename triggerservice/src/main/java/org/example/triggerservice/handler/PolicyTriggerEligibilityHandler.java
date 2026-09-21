package org.example.triggerservice.handler;

import lombok.RequiredArgsConstructor;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.service.PolicyTriggerEligibilityService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PolicyTriggerEligibilityHandler{
    private final PolicyTriggerEligibilityService eligibilityService;

    public Mono<ServerResponse> checkEligibility(ServerRequest request){
        return parseId(request,"policyId")
                .flatMap(eligibilityService::checkEligibility)
                .flatMap(response->ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
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
            return Mono.error(
                    new ValidationException("Invalid "+variable)
            );
        }
    }
}