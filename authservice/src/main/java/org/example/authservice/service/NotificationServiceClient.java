package org.example.authservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.authservice.dto.AuditEventRequest;
import org.example.authservice.util.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationServiceClient{
    private final WebClient webClient;

    public NotificationServiceClient(
            WebClient.Builder loadBalancedWebClientBuilder,
            @Value("${gateway.internal-key:cityshield-local-gateway-key}") String internalKey
    ){
        this.webClient=loadBalancedWebClientBuilder.clone()
                .baseUrl("http://NOTIFICATION-SERVICE")
                .defaultHeader(GatewayHeaders.GATEWAY_KEY,internalKey)
                .build();
    }

    /**
     * Records an entry in the acting user's audit history. Login and registration
     * run before the user is authenticated, so the actor is passed explicitly;
     * when it is null the signed-in user (e.g. an admin) is used. Failures are
     * logged and never break the calling workflow.
     */
    public Mono<Void> audit(
            String eventType,
            String entityType,
            Long entityId,
            Long actorUserId,
            String actorRole,
            String description
    ){
        return auditChange(eventType,entityType,entityId,actorUserId,actorRole,description,null,null);
    }

    /** Audit entry that also records the value before and after the change. */
    public Mono<Void> auditChange(
            String eventType,
            String entityType,
            Long entityId,
            Long actorUserId,
            String actorRole,
            String description,
            String oldValue,
            String newValue
    ){
        Mono<AuditEventRequest> request=(actorUserId!=null
                ?Mono.just(build(eventType,entityType,entityId,actorUserId,actorRole,description))
                :currentAuthentication()
                .map(auth->build(eventType,entityType,entityId,
                        Long.valueOf(auth.getName()),roleOf(auth),description))
                .defaultIfEmpty(build(eventType,entityType,entityId,null,"SYSTEM",description)))
                .doOnNext(body->{
                    body.setOldValue(oldValue);
                    body.setNewValue(newValue);
                });

        return request
                .flatMap(body->webClient.post()
                        .uri("/internal/audit-events")
                        .bodyValue(body)
                        .retrieve()
                        .toBodilessEntity()
                        .then()
                        .retryWhen(TEMPORARY_FAILURE_RETRY))
                .onErrorResume(error->{
                    log.warn("Audit event {} failed: {}",eventType,error.getMessage());
                    return Mono.empty();
                });
    }

    private static AuditEventRequest build(
            String eventType,String entityType,Long entityId,
            Long actorUserId,String actorRole,String description){
        return AuditEventRequest.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorUserId)
                .actorRole(actorRole)
                .description(description)
                .build();
    }

    private static Mono<Authentication> currentAuthentication(){
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(auth->auth.isAuthenticated()
                        &&auth.getName()!=null
                        &&auth.getName().matches("\\d+"));
    }

    private static String roleOf(Authentication auth){
        return auth.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse(null);
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
