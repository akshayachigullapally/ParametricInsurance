package org.example.financialservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.financialservice.dto.InternalClaimResponse;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class ClaimsServiceClient{
    private final WebClient webClient;

    public ClaimsServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key:climatesure-local-gateway-key}") String internalKey
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
        log.debug("Find claim [claims service]: claimId={}",claimId);
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

    /**
     * Moves the claim to SETTLED once its payout succeeded. A failure is logged,
     * never propagated: the money has already been paid.
     */
    public Mono<Void> settleClaim(Long claimId,String payoutReference){
        return webClient.put()
                .uri(uriBuilder->uriBuilder
                        .path("/internal/claims/{claimId}/settle")
                        .queryParam("payoutReference",payoutReference)
                        .build(claimId))
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(ignored->log.info(
                        "Claim {} settled after payout {}",claimId,payoutReference))
                .onErrorResume(error->{
                    log.warn("Could not settle claim {} after payout {}: {}",
                            claimId,payoutReference,error.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
