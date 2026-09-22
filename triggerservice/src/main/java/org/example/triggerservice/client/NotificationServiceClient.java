package org.example.triggerservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.InternalNotificationRequest;
import org.example.triggerservice.dto.NotificationResponse;
import org.example.triggerservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationServiceClient{
    private final WebClient.Builder webClientBuilder;

    @Value("${gateway.internal-key}")
    private String internalKey;

    public Mono<NotificationResponse> createInternalNotification(InternalNotificationRequest request){
        return webClientBuilder.build()
                .post()
                .uri("http://NOTIFICATION-SERVICE/internal/notifications")
                .header(GatewayHeaders.GATEWAY_KEY,internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NotificationResponse.class)
                .doOnError(error->log.error(
                        "Failed to create internal notification: {}",
                        error.getMessage()));
    }
}