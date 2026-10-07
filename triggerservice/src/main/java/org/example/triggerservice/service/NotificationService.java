package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.AuditEventRequest;
import org.example.triggerservice.dto.NotificationRequest;
import org.springframework.stereotype.Component;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationService {

    private final NotificationServiceClient client;

    public NotificationService(NotificationServiceClient client) {
        this.client = client;
    }

    public Mono<Void> audit(String eventType, String entityType, Long entityId, Long actorId, String details) {
        return auditChange(eventType, entityType, entityId, actorId, details, null, null);
    }

    /** Audit entry that also records the value before and after the change. */
    public Mono<Void> auditChange(String eventType, String entityType, Long entityId, Long actorId,
                                  String details, String oldValue, String newValue) {
        return currentActor()
                .defaultIfEmpty(new AuditActor(actorId, actorId == null ? "SYSTEM" : null))
                .flatMap(actor -> client.sendAuditEvent(AuditEventRequest.builder()
                        .eventType(eventType)
                        .entityType(entityType)
                        .entityId(entityId)
                        .actorUserId(actor.userId())
                        .actorRole(actor.role())
                        .description(details)
                        .oldValue(oldValue)
                        .newValue(newValue)
                        .build()))
                .onErrorResume(e -> {
                    log.warn("Audit event {} failed: {}", eventType, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> notifyTriggerVerified(Long customerId, Long eventId, String eventReference) {
        log.debug("Notify trigger verified: customerId={}, eventId={}, eventReference={}",customerId,eventId,eventReference);
        return notify(customerId, "TRIGGER_VERIFIED", "Trigger Event Verified",
                "Trigger event " + eventReference + " has been verified. You may now submit a claim.",
                "TRIGGER_EVENT", eventId);
    }

    public Mono<Void> notifyTriggerRejected(Long customerId, Long eventId, String eventReference) {
        log.debug("Notify trigger rejected: customerId={}, eventId={}, eventReference={}",customerId,eventId,eventReference);
        return notify(customerId, "TRIGGER_REJECTED", "Trigger Event Rejected",
                "Trigger event " + eventReference + " has been rejected.",
                "TRIGGER_EVENT", eventId);
    }

    private Mono<Void> notify(Long customerId, String type, String title, String message,
                              String entityType, Long entityId) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientCustomerId(customerId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(entityType)
                .relatedEntityId(entityId)
                .build();
        return client.sendNotification(request)
                .onErrorResume(e -> {
                    log.error("Notification {} was NOT delivered, so nobody will see it. Check that NOTIFICATION-SERVICE is running and registered in Eureka (http://localhost:8761). Cause: {}", type, e.toString());
                    return Mono.empty();
                });
    }

    // The signed-in user behind the current request. Empty for scheduled jobs and
    // service-to-service calls, which carry no user identity.
    private Mono<AuditActor> currentActor() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(auth -> auth.isAuthenticated()
                        && auth.getName() != null
                        && auth.getName().matches("\\d+"))
                .map(auth -> new AuditActor(
                        Long.valueOf(auth.getName()),
                        auth.getAuthorities().stream()
                                .findFirst()
                                .map(GrantedAuthority::getAuthority)
                                .orElse(null)
                ));
    }

    private record AuditActor(Long userId, String role) {}
}
