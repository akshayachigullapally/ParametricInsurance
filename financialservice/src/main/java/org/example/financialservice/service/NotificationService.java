package org.example.financialservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.financialservice.dto.AuditEventRequest;
import org.example.financialservice.dto.NotificationRequest;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class NotificationService{
    private final NotificationServiceClient client;

    public NotificationService(NotificationServiceClient client){
        this.client=client;
    }

    public Mono<Void> audit(
            String eventType,
            String entityType,
            Long entityId,
            Long actorId,
            String details
    ){
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
                .onErrorResume(exception->{
                    log.warn(
                            "Audit event {} failed: {}",
                            eventType,
                            exception.getMessage()
                    );
                    return Mono.empty();
                });
    }

    public Mono<Void> notifyPaymentSuccess(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "PAYMENT_SUCCESS",
                "Payment successful",
                "Payment "+reference+" of "+amount+" succeeded",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPaymentFailed(
            Long customerId,
            String reference,
            String reason
    ){
        return notify(
                customerId,
                "PAYMENT_FAILED",
                "Payment failed",
                "Payment "+reference+" failed"+
                        (reason!=null&&!reason.isBlank()
                                ?": "+reason
                                :""),
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPayoutProcessed(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "PAYOUT_PROCESSED",
                "Payout processed",
                "Payout "+reference+" of "+amount+" processed",
                "PAYOUT",
                null
        );
    }

    public Mono<Void> notifyPayoutFailed(
            Long customerId,
            String reference,
            String reason
    ){
        return notify(
                customerId,
                "PAYOUT_FAILED",
                "Payout failed",
                "Payout "+reference+" failed"+
                        (reason!=null&&!reason.isBlank()
                                ?": "+reason
                                :""),
                "PAYOUT",
                null
        );
    }

    public Mono<Void> notifyPaymentPending(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "PAYMENT_PENDING",
                "Payment pending",
                "Payment "+reference+" of "+amount+
                        " is pending confirmation",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPaymentRetry(
            Long customerId,
            String reference,
            BigDecimal amount,
            Integer attempt
    ){
        return notify(
                customerId,
                "PAYMENT_RETRY",
                "Payment retry initiated",
                "Retrying payment "+reference+" of "+amount+
                        " (attempt "+(attempt!=null?attempt:1)+")",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPaymentRefunded(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "PAYMENT_REFUNDED",
                "Payment refunded",
                "Payment "+reference+" of "+amount+
                        " has been refunded",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPayoutPending(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "PAYOUT_PENDING",
                "Payout pending",
                "Payout "+reference+" of "+amount+
                        " is pending processing",
                "PAYOUT",
                null
        );
    }

    public Mono<Void> notifyPayoutRetry(
            Long customerId,
            String reference,
            BigDecimal amount,
            Integer retryCount
    ){
        return notify(
                customerId,
                "PAYOUT_RETRY",
                "Payout retry initiated",
                "Retrying payout "+reference+" of "+amount+
                        " (retry "+(retryCount!=null?retryCount:1)+")",
                "PAYOUT",
                null
        );
    }

    public Mono<Void> notifyQuoteCreated(
            Long customerId,
            String reference,
            BigDecimal premium,
            Long quoteId
    ){
        return notify(
                customerId,
                "QUOTE_CREATED",
                "Quote created",
                "Quote "+reference+" created for premium "+premium,
                "QUOTE",
                quoteId
        );
    }

    public Mono<Void> notifyQuoteExpired(
            Long customerId,
            String reference,
            Long quoteId
    ){
        return notify(
                customerId,
                "QUOTE_EXPIRED",
                "Quote expired",
                "Quote "+reference+" has expired",
                "QUOTE",
                quoteId
        );
    }

    public Mono<Void> notifyQuoteConsumed(
            Long customerId,
            String reference,
            String policyNumber,
            Long quoteId
    ){
        return notify(
                customerId,
                "QUOTE_CONSUMED",
                "Quote consumed",
                "Quote "+reference+" was consumed for policy "+
                        policyNumber,
                "QUOTE",
                quoteId
        );
    }

    public Mono<Void> notifyPremiumDue(
            Long customerId,
            String policyNumber,
            BigDecimal amount,
            LocalDate dueDate
    ){
        return notify(
                customerId,
                "PREMIUM_DUE",
                "Premium payment due",
                "Premium of "+amount+" for policy "+policyNumber+
                        " is due by "+dueDate,
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPremiumOverdue(
            Long customerId,
            String policyNumber,
            BigDecimal amount,
            LocalDate dueDate
    ){
        return notify(
                customerId,
                "PREMIUM_OVERDUE",
                "Premium payment overdue",
                "Premium of "+amount+" for policy "+policyNumber+
                        " is overdue, expected by "+dueDate,
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyRefundProcessed(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        return notify(
                customerId,
                "REFUND_PROCESSED",
                "Refund processed",
                "Refund of "+amount+" for payment "+reference+
                        " has been processed",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyRefundFailed(
            Long customerId,
            String reference,
            BigDecimal amount,
            String reason
    ){
        return notify(
                customerId,
                "REFUND_FAILED",
                "Refund failed",
                "Refund of "+amount+" for payment "+reference+
                        " failed"+
                        (reason!=null&&!reason.isBlank()
                                ?": "+reason
                                :""),
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyReconciliationCompleted(String reference){
        return notifyRole(
                "ADMIN",
                "RECONCILIATION_COMPLETED",
                "Reconciliation completed",
                "Reconciliation run "+reference+
                        " completed with no discrepancies",
                null,
                null
        );
    }

    public Mono<Void> notifyReconciliationDiscrepancy(
            String reference,
            int discrepancyCount
    ){
        return notifyRole(
                "ADMIN",
                "RECONCILIATION_DISCREPANCY",
                "Reconciliation discrepancy found",
                "Reconciliation run "+reference+" found "+
                        discrepancyCount+
                        " discrepancy/discrepancies",
                null,
                null
        );
    }

    public Mono<Void> notifyPricingRuleCreated(
            Long actorId,
            Long productId,
            Long ruleId
    ){
        return notify(
                actorId,
                "PRICING_RULE_CREATED",
                "Pricing rule created",
                "Pricing rule created for product "+productId,
                "PRICING_RULE",
                ruleId
        );
    }

    public Mono<Void> notifyPricingRuleUpdated(
            Long actorId,
            Long productId,
            Long ruleId
    ){
        return notify(
                actorId,
                "PRICING_RULE_UPDATED",
                "Pricing rule updated",
                "Pricing rule for product "+productId+" was updated",
                "PRICING_RULE",
                ruleId
        );
    }

    private Mono<Void> notifyRole(
            String role,
            String type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ){
        NotificationRequest request=NotificationRequest.builder()
                .recipientRole(role)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(referenceType)
                .relatedEntityId(referenceId)
                .build();

        return client.sendNotification(request)
                .onErrorResume(exception->{
                    log.warn(
                            "Notification {} failed: {}",
                            type,
                            exception.getMessage()
                    );
                    return Mono.empty();
                });
    }

    private Mono<Void> notify(
            Long customerId,
            String type,
            String title,
            String message,
            String referenceType,
            Long referenceId
    ){
        NotificationRequest request=NotificationRequest.builder()
                .recipientCustomerId(customerId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(referenceType)
                .relatedEntityId(referenceId)
                .build();

        return client.sendNotification(request)
                .onErrorResume(exception->{
                    log.warn(
                            "Notification {} failed: {}",
                            type,
                            exception.getMessage()
                    );
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