package org.example.financialservice.service;

import org.example.financialservice.dto.InternalClaimResponse;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ClaimsServiceClient{
    private final WebClient webClient;

    public ClaimsServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key}") String internalKey
    ){
        this.webClient=builder
                .baseUrl("http://CLAIMS-SERVICE")
                .defaultHeader(
                        GatewayHeaders.GATEWAY_KEY,
                        internalKey
                )
                .build();
    }

    public Mono<InternalClaimResponse> findClaim(Long claimId){
        return webClient.get()
                .uri("/internal/claims/{claimId}",claimId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response->Mono.error(
                                new NotFoundException(
                                        "Claim "+claimId+" not found"
                                )
                        )
                )
                .bodyToMono(InternalClaimResponse.class);
    }
}