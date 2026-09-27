package org.example.productservice.service;

import org.example.productservice.dto.AuditEventRequest;
import org.example.productservice.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
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

    public Mono<Void> notifyPolicySubmitted(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_SUBMITTED", "Policy Submitted",
                "Your policy application " + policyNumber + " has been submitted and is pending review.",
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyPendingReview(Long policyId, String policyNumber) {
        String message = "Policy application " + policyNumber + " was submitted and is awaiting underwriter review.";
        return notifyRole("ROLE_UNDERWRITER", "POLICY_SUBMITTED", "Policy Pending Review",
                message, "POLICY", policyId)
                .then(notifyRole("ROLE_ADMIN", "POLICY_SUBMITTED", "Policy Pending Review",
                        message, "POLICY", policyId));
    }

    public Mono<Void> notifyPolicyApproved(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_APPROVED", "Policy Approved",
                "Your policy " + policyNumber + " has been approved and is now active.",
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyRejected(Long customerId, Long policyId, String policyNumber, String reason) {
        return notify(customerId, "POLICY_REJECTED", "Policy Rejected",
                "Your policy application " + policyNumber + " has been rejected." +
                        (reason != null && !reason.isBlank() ? " Reason: " + reason : ""),
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyCancelled(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_CANCELLED", "Policy Cancelled",
                "Your policy " + policyNumber + " has been cancelled.",
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyExpired(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_EXPIRED", "Policy Expired",
                "Your policy " + policyNumber + " has expired.",
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyRenewalDue(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_RENEWAL_DUE", "Policy Renewal Due",
                "Your policy " + policyNumber + " is due for renewal.",
                "POLICY", policyId);
    }

    public Mono<Void> notifyPolicyDocumentRequested(Long customerId, Long policyId, String policyNumber) {
        return notify(customerId, "POLICY_DOCUMENT_REQUESTED", "Policy Document Requested",
                "A document has been requested for policy " + policyNumber + ".",
                "POLICY", policyId);
    }

    public Mono<Void> notifyProductCreated(Long productId, String productName) {
        return notifyRole("ROLE_CUSTOMER", "PRODUCT_CREATED", "New Product Available",
                "Product " + productName + " is now available on the platform.",
                "PRODUCT", productId);
    }

    public Mono<Void> notifyProductUpdated(Long productId, String productName) {
        return notifyRole("ROLE_CUSTOMER", "PRODUCT_UPDATED", "Product Updated",
                "Product " + productName + " details have been updated.",
                "PRODUCT", productId);
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
