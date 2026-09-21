package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.AuditEventRequest;
import org.example.triggerservice.dto.NotificationRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationService {

    private final NotificationServiceClient client;

    public NotificationService(NotificationServiceClient client) {
        this.client = client;
    }

    public Mono<Void> audit(String eventType, String entityType, Long entityId, Long actorId, String details) {
        AuditEventRequest request = AuditEventRequest.builder()
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .actorUserId(actorId)
                .description(details)
                .build();
        return client.sendAuditEvent(request)
                .onErrorResume(e -> {
                    log.warn("Audit event {} failed: {}", eventType, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> notifyTriggerDetected(Long customerId, Long eventId, String eventReference) {
        return notify(customerId, "TRIGGER_DETECTED", "Trigger Event Detected",
                "A trigger event " + eventReference + " has been detected and is pending verification.",
                "TRIGGER_EVENT", eventId);
    }

    public Mono<Void> notifyTriggerVerified(Long customerId, Long eventId, String eventReference) {
        return notify(customerId, "TRIGGER_VERIFIED", "Trigger Event Verified",
                "Trigger event " + eventReference + " has been verified. You may now submit a claim.",
                "TRIGGER_EVENT", eventId);
    }

    public Mono<Void> notifyTriggerRejected(Long customerId, Long eventId, String eventReference) {
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
                    log.warn("Notification {} failed: {}", type, e.getMessage());
                    return Mono.empty();
                });
    }
}
