package org.example.claimsservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.claimsservice.dto.PolicyTriggerEligibilityResponse;
import org.example.claimsservice.exception.DependentServiceException;
import org.example.claimsservice.exception.NotFoundException;
import org.example.claimsservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerEligibilityClient{
    private final WebClient.Builder loadBalancedWebClientBuilder;

    @Value("${gateway.internal-key}")
    private String internalKey;

    public Mono<PolicyTriggerEligibilityResponse> getEligibility(Long policyId){
        return loadBalancedWebClientBuilder.build()
                .get()
                .uri(
                        "http://TRIGGER-SERVICE/internal/triggers/policy/{policyId}/eligibility",
                        policyId
                )
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .retrieve()
                .onStatus(
                        status->status.value()==404,
                        response->Mono.error(
                                new NotFoundException(
                                        "Policy trigger eligibility was not found"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Trigger eligibility validation failed"
                                )
                        )
                )
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        response->Mono.error(
                                new DependentServiceException(
                                        "Trigger service is currently unavailable"
                                )
                        )
                )
                .bodyToMono(PolicyTriggerEligibilityResponse.class)
                .switchIfEmpty(Mono.error(
                        new DependentServiceException(
                                "Trigger service returned an empty eligibility response"
                        )
                ))
                .onErrorMap(
                        WebClientRequestException.class,
                        exception->{
                            log.error("Could not reach trigger service for policy {}: {}",
                                    policyId,exception.getMessage());
                            return new DependentServiceException(
                                    "Trigger service is currently unavailable"
                            );
                        }
                );
    }
}