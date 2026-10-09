package org.example.claimsservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.claimsservice.dto.CreatePayoutRequest;
import org.example.claimsservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialServiceClient{
    private final WebClient.Builder loadBalancedWebClientBuilder;

    @Value("${gateway.internal-key:climatesure-local-gateway-key}")
    private String internalKey;

    public Mono<Void> createPayoutForClaim(Long claimId){
        return loadBalancedWebClientBuilder.build()
                .post()
                .uri("http://FINANCIAL-SERVICE/internal/financial/payouts")
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(CreatePayoutRequest.builder()
                        .claimId(claimId)
                        .build())
                .retrieve()
                .bodyToMono(Object.class)
                .then()
                .doOnError(error->log.warn(
                        "Failed to auto-create payout for approved claim {}: {}",
                        claimId,error.getMessage()
                ))
                .onErrorResume(error->Mono.empty());
    }

    /** Whether the policy's latest premium payment succeeded; unreachable counts as unpaid. */
    public Mono<Boolean> isPremiumPaid(Long policyId,String policyNumber){
        return loadBalancedWebClientBuilder.build()
                .get()
                .uri(builder->builder.scheme("http").host("FINANCIAL-SERVICE")
                        .path("/internal/financial/payments/policy/{policyId}/status")
                        .queryParamIfPresent("policyNumber",java.util.Optional.ofNullable(policyNumber))
                        .build(policyId))
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .map(body->Boolean.TRUE.equals(body.get("successful")))
                .defaultIfEmpty(false)
                .onErrorResume(error->{
                    log.warn("Payment status unavailable for policy {}: {}",
                            policyId,error.getMessage());
                    return Mono.just(false);
                });
    }
}
