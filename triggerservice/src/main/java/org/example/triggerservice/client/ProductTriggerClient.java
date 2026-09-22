package org.example.triggerservice.client;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.VerifiedEventRequest;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
}