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
public class InternalNotificationClient{
    private final WebClient.Builder webClientBuilder;

    @Value("${gateway.internal-key:cityshield-local-gateway-key}")
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
                .retryWhen(TEMPORARY_FAILURE_RETRY)
                .doOnError(error->log.error(
                        "Failed to create internal notification: {}",
                        error.getMessage()));
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
