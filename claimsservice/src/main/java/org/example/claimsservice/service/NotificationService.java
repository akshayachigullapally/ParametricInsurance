package org.example.claimsservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.claimsservice.dto.AuditEventRequest;
import org.example.claimsservice.dto.NotificationRequest;
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
        return currentActor()
                .defaultIfEmpty(new AuditActor(actorId, actorId == null ? "SYSTEM" : null))
                .flatMap(actor -> client.sendAuditEvent(AuditEventRequest.builder()
                        .eventType(eventType)
                        .entityType(entityType)
                        .entityId(entityId)
                        .actorUserId(actor.userId())
                        .actorRole(actor.role())
                        .description(details)
                        .build()))
                .onErrorResume(e -> {
                    log.warn("Audit event {} failed: {}", eventType, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> notifyClaimSubmitted(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "CLAIM_SUBMITTED", "Claim Submitted",
                "Your claim " + claimNumber + " has been submitted and is pending review.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyManualClaimSubmitted(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "MANUAL_CLAIM_SUBMITTED", "Manual claim received",
                "Your claim " + claimNumber + " was received and requires a Claims Officer " +
                        "investigation because no automatic trigger event was found.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimUnderReview(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "CLAIM_UNDER_REVIEW", "Claim Under Review",
                "Your claim " + claimNumber + " is now under review.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimApproved(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "CLAIM_APPROVED", "Claim Approved",
                "Your claim " + claimNumber + " has been approved.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimRejected(Long customerId, Long claimId, String claimNumber, String reason) {
        return notify(customerId, "CLAIM_REJECTED", "Claim Rejected",
                "Your claim " + claimNumber + " has been rejected." +
                        (reason != null && !reason.isBlank() ? " Reason: " + reason : ""),
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimSettled(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "CLAIM_SETTLED", "Claim Settled",
                "Your claim " + claimNumber + " has been settled.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimCancelled(Long customerId, Long claimId, String claimNumber) {
        return notify(customerId, "CLAIM_CANCELLED", "Claim Cancelled",
                "Your claim " + claimNumber + " has been cancelled.",
                "CLAIM", claimId);
    }

    public Mono<Void> notifyClaimPendingReview(Long claimId, String claimNumber) {
        String message = "Claim " + claimNumber + " was submitted and is awaiting Claims Officer review.";
        return notifyRole("ROLE_CLAIMS_OFFICER", "CLAIM_SUBMITTED", "Claim Pending Review",
                message, "CLAIM", claimId)
                .then(notifyRole("ROLE_ADMIN", "CLAIM_SUBMITTED", "Claim Pending Review",
                        message, "CLAIM", claimId));
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

    private Mono<Void> notifyRole(String role, String type, String title, String message,
                                  String entityType, Long entityId) {
        NotificationRequest request = NotificationRequest.builder()
                .recipientRole(role)
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
