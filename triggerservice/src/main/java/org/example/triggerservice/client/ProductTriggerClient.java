package org.example.triggerservice.client;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.VerifiedEventRequest;
import org.example.triggerservice.dto.InternalProductConfig;
import org.example.triggerservice.dto.EligiblePolicyResponse;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class ProductTriggerClient{
    private final WebClient webClient;

    public ProductTriggerClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key}") String internalKey
    ){
        this.webClient=builder.baseUrl("http://PRODUCT-SERVICE")
                .defaultHeader(GatewayHeaders.GATEWAY_KEY,internalKey)
                .build();
    }

    public Mono<Void> notifyVerifiedTriggerEvent(
            Long productId,
            Long eventId
    ){
        return webClient.post()
                .uri("/internal/products/verified-event")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VerifiedEventRequest.builder()
                        .productId(productId)
                        .eventId(eventId)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error->log.error(
                        "Failed to notify product service about verified event {}: {}",
                        eventId,error.getMessage()));
    }

        public Mono<InternalProductConfig> getProduct(Long productId){
            log.debug("Get product: productId={}",productId);
                return webClient.get()
                                .uri("/internal/products/{productId}",productId)
                                .retrieve()
                                .bodyToMono(InternalProductConfig.class);
        }

                    public Flux<org.example.triggerservice.dto.ActivePolicyForTriggerResponse> getActivePolicies(
                            Long productId,Long locationZoneId){
                        log.debug("Get active policies [product trigger service]: productId={}, locationZoneId={}",productId,locationZoneId);
                        return webClient.get()
                                .uri(uriBuilder->uriBuilder
                                        .path("/internal/policies/active-for-trigger")
                                        .queryParam("productId",productId)
                                        .queryParam("locationZoneId",locationZoneId)
                                        .build())
                                .retrieve()
                                .bodyToFlux(org.example.triggerservice.dto.ActivePolicyForTriggerResponse.class);
                    }

                    public Mono<Boolean> hasActivePoliciesForZone(Long zoneId){
                        log.debug("Has active policies for zone [product trigger service]: zoneId={}",zoneId);
                        return webClient.get()
                                .uri("/internal/policies/zone/{zoneId}/has-active",zoneId)
                                .retrieve()
                                .bodyToMono(java.util.Map.class)
                                .map(body->Boolean.TRUE.equals(body.get("hasActivePolicies")));
                    }

                    public Flux<EligiblePolicyResponse> getEligiblePoliciesForEvent(
                            Long productId,Long locationZoneId,java.time.LocalDateTime eventStart,
                            java.time.LocalDateTime eventEnd,Long triggerEventId){
                        log.debug("Get eligible policies for event [product trigger service]: productId={}, locationZoneId={}, triggerEventId={}",productId,locationZoneId,triggerEventId);
                        return webClient.get()
                                .uri(uriBuilder->uriBuilder.path("/internal/policies/active-for-trigger")
                                        .queryParam("productId",productId)
                                        .queryParam("locationZoneId",locationZoneId)
                                        .build())
                                .retrieve()
                                .bodyToFlux(EligiblePolicyResponse.class);
                    }
}