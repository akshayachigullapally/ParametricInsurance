package org.example.claimsservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.claimsservice.dto.AuditEventRequest;
import org.example.claimsservice.dto.NotificationRequest;
import org.example.claimsservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationServiceClient {

    private final WebClient webClient;

    public NotificationServiceClient(
            WebClient.Builder builder,
            @Value("${gateway.internal-key:cityshield-local-gateway-key}") String internalKey
    ) {
        this.webClient = builder
                .baseUrl("http://NOTIFICATION-SERVICE")
                .defaultHeader(GatewayHeaders.GATEWAY_KEY, internalKey)
                .build();
    }

    public Mono<Void> sendNotification(NotificationRequest request) {
        log.debug("Send notification");
        return webClient.post()
                .uri("/internal/notifications")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then()
                .retryWhen(TEMPORARY_FAILURE_RETRY);
    }

    public Mono<Void> sendAuditEvent(AuditEventRequest request) {
        log.debug("Send audit event");
        return webClient.post()
                .uri("/internal/audit-events")
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then()
                .retryWhen(TEMPORARY_FAILURE_RETRY);
    }

    /**
     * The notification service may be briefly unreachable, e.g. just after it (re)starts and
     * before every service has its new address. Retry such temporary failures a few times
     * (0.5 s, 1 s, 2 s) instead of losing the notification; real errors are not retried.
     */
    private static final reactor.util.retry.Retry TEMPORARY_FAILURE_RETRY=
            reactor.util.retry.Retry.backoff(3,java.time.Duration.ofMillis(500))
                    .filter(NOTIFICATION_RETRY_FILTER())
                    .onRetryExhaustedThrow((spec,signal)->signal.failure());

    private static java.util.function.Predicate<Throwable> NOTIFICATION_RETRY_FILTER(){
        return error->{
            if(error instanceof org.springframework.web.reactive.function.client.WebClientRequestException){
                return true;
            }
            if(error instanceof org.springframework.web.reactive.function.client.WebClientResponseException response){
                int status=response.getStatusCode().value();
                return status==502||status==503||status==504;
            }
            return false;
        };
    }
}
