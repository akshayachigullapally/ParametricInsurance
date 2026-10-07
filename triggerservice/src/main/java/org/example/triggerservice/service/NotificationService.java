package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
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
}
