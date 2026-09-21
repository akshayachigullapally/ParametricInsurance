package org.example.triggerservice.service;

import org.example.triggerservice.dto.AuditEventRequest;
import org.example.triggerservice.dto.NotificationRequest;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NotificationServiceClient {

    private final WebClient webClient;

    public NotificationServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key}") String internalKey
    ) {
        this.webClient = builder
                .baseUrl("http://NOTIFICATION-SERVICE")
                .defaultHeader(GatewayHeaders.GATEWAY_KEY, internalKey)
                .build();
    }

    public Mono<Void> sendNotification(NotificationRequest request) {
        return webClient.post()
                .uri("/internal/notifications")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public Mono<Void> sendAuditEvent(AuditEventRequest request) {
        return webClient.post()
                .uri("/internal/audit-events")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then();
    }
}
