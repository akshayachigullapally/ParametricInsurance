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
        return auditChange(eventType,entityType,entityId,actorId,details,null,null);
    }

    /** Audit entry that also records the value before and after the change. */
    public Mono<Void> auditChange(
            String eventType,
            String entityType,
            Long entityId,
            Long actorId,
            String details,
            String oldValue,
            String newValue
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
                        .oldValue(oldValue)
                        .newValue(newValue)
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
        log.debug("Notify payment success: customerId={}, reference={}, amount={}",customerId,reference,amount);
        return notify(
                customerId,
                "PAYMENT_SUCCESS",
                "Payment successful",
                "Payment "+reference+" of "+amount+" succeeded",
                "PAYMENT",
                null
        );
    }

    public Mono<Void> notifyPayoutProcessed(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        log.debug("Notify payout processed: customerId={}, reference={}, amount={}",customerId,reference,amount);
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
        log.debug("Notify payout failed: customerId={}, reference={}, reason={}",customerId,reference,reason);
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

    public Mono<Void> notifyPaymentRefunded(
            Long customerId,
            String reference,
            BigDecimal amount
    ){
        log.debug("Notify payment refunded: customerId={}, reference={}, amount={}",customerId,reference,amount);
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
        log.debug("Notify payout pending: customerId={}, reference={}, amount={}",customerId,reference,amount);
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
        log.debug("Notify payout retry: customerId={}, reference={}, amount={}, retryCount={}",customerId,reference,amount,retryCount);
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
        log.debug("Notify quote created: customerId={}, reference={}, premium={}, quoteId={}",customerId,reference,premium,quoteId);
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
        log.debug("Notify quote expired: customerId={}, reference={}, quoteId={}",customerId,reference,quoteId);
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
        log.debug("Notify quote consumed: customerId={}, reference={}, policyNumber={}, quoteId={}",customerId,reference,policyNumber,quoteId);
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
        log.debug("Notify premium due: customerId={}, policyNumber={}, amount={}, dueDate={}",customerId,policyNumber,amount,dueDate);
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
        log.debug("Notify premium overdue: customerId={}, policyNumber={}, amount={}, dueDate={}",customerId,policyNumber,amount,dueDate);
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
        log.debug("Notify refund processed: customerId={}, reference={}, amount={}",customerId,reference,amount);
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

    public Mono<Void> notifyReconciliationCompleted(String reference){
        log.debug("Notify reconciliation completed: reference={}",reference);
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
        log.debug("Notify reconciliation discrepancy: reference={}, discrepancyCount={}",reference,discrepancyCount);
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
        log.debug("Notify pricing rule created: actorId={}, productId={}, ruleId={}",actorId,productId,ruleId);
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
        log.debug("Notify pricing rule updated: actorId={}, productId={}, ruleId={}",actorId,productId,ruleId);
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
                    log.error(
                            "Notification {} was NOT delivered, so nobody will see it. Check that NOTIFICATION-SERVICE is running and registered in Eureka (http://localhost:8761). Cause: {}",
                            type,
                            exception.toString()
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
                    log.error(
                            "Notification {} was NOT delivered, so nobody will see it. Check that NOTIFICATION-SERVICE is running and registered in Eureka (http://localhost:8761). Cause: {}",
                            type,
                            exception.toString()
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