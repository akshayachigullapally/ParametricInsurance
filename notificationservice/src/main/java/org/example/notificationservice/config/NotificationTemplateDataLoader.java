package org.example.notificationservice.config;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.model.DefaultTemplate;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationTemplateDataRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Component
public class NotificationTemplateDataLoader implements ApplicationRunner {

    private final NotificationTemplateDataRepository repository;

    public NotificationTemplateDataLoader(NotificationTemplateDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        repository.count()
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    if (count == null || count == 0) {
                        return Mono.fromRunnable(this::seed);
                    }
                    return Mono.empty();
                })
                .subscribe(
                        ignored -> log.info("Default notification templates seed complete"),
                        error -> log.warn("Failed to seed default notification templates: {}", error.getMessage()));
    }

    private void seed() {
        Map<NotificationType, TemplateSpec> defaults = defaults();
        defaults.forEach((type, spec) ->
                repository.findByNotificationType(type)
                        .switchIfEmpty(repository.save(DefaultTemplate.builder()
                                .notificationType(type)
                                .titleTemplate(spec.title)
                                .messageTemplate(spec.message)
                                .defaultChannel(spec.channel)
                                .active(true)
                                .createdAt(LocalDateTime.now())
                                .build()))
                        .subscribe());
    }

    private Map<NotificationType, TemplateSpec> defaults() {
        Map<NotificationType, TemplateSpec> map = new EnumMap<>(NotificationType.class);
        map.put(NotificationType.POLICY_SUBMITTED,
                new TemplateSpec("Policy Submitted", "Your policy {policyNumber} has been submitted and is under review.", DeliveryChannel.IN_APP));
        map.put(NotificationType.POLICY_APPROVED,
                new TemplateSpec("Policy Approved", "Great news! Your policy {policyNumber} has been approved.", DeliveryChannel.IN_APP));
        map.put(NotificationType.POLICY_REJECTED,
                new TemplateSpec("Policy Rejected", "Unfortunately your policy {policyNumber} could not be approved.", DeliveryChannel.EMAIL));
        map.put(NotificationType.POLICY_CANCELLED,
                new TemplateSpec("Policy Cancelled", "Your policy {policyNumber} has been cancelled.", DeliveryChannel.EMAIL));
        map.put(NotificationType.POLICY_EXPIRED,
                new TemplateSpec("Policy Expired", "Your policy {policyNumber} has expired on {endDate}.", DeliveryChannel.EMAIL));
        map.put(NotificationType.POLICY_RENEWAL_DUE,
                new TemplateSpec("Policy Renewal Due", "Your policy {policyNumber} is due for renewal on {endDate}. Please renew to stay covered.", DeliveryChannel.EMAIL));
        map.put(NotificationType.POLICY_DOCUMENT_REQUESTED,
                new TemplateSpec("Policy Document Requested", "A document has been requested for policy {policyNumber}. Please provide it in the portal.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PRODUCT_CREATED,
                new TemplateSpec("New Product Available", "Product {productName} is now available on the platform.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PRODUCT_UPDATED,
                new TemplateSpec("Product Updated", "Product {productName} details have been updated.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_SUBMITTED,
                new TemplateSpec("Claim Submitted", "Your claim {claimNumber} has been submitted and will be reviewed.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_UNDER_REVIEW,
                new TemplateSpec("Claim Under Review", "Your claim {claimNumber} is currently under review.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_APPROVED,
                new TemplateSpec("Claim approved", "Your claim {claimNumber} has been approved for {approvedAmount}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_REJECTED,
                new TemplateSpec("Claim Rejected", "Your claim {claimNumber} has been rejected.", DeliveryChannel.EMAIL));
        map.put(NotificationType.CLAIM_CANCELLED,
                new TemplateSpec("Claim Cancelled", "Your claim {claimNumber} has been cancelled.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_SETTLED,
                new TemplateSpec("Claim Settled", "Your claim {claimNumber} has been settled. The payout will be processed shortly.", DeliveryChannel.IN_APP));
        map.put(NotificationType.MANUAL_CLAIM_SUBMITTED,
                new TemplateSpec("Manual claim received", "Your claim {claimNumber} was received and will be reviewed by a Claims Officer because no eligible automatic trigger event was found.", DeliveryChannel.IN_APP));
        map.put(NotificationType.AUTOMATIC_CLAIM_CREATED,
                new TemplateSpec("Automatic Claim Created", "An automatic claim {claimNumber} was created for your policy {policyNumber} based on the verified trigger event.", DeliveryChannel.IN_APP));
        map.put(NotificationType.MANUAL_CLAIM_INVESTIGATION_STARTED,
                new TemplateSpec("Investigation Started", "A Claims Officer has started investigating your manual claim {claimNumber}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.MANUAL_CLAIM_INVESTIGATION_COMPLETED,
                new TemplateSpec("Investigation Completed", "The investigation of your manual claim {claimNumber} has been completed.", DeliveryChannel.IN_APP));
        map.put(NotificationType.CLAIM_DOCUMENT_REQUESTED,
                new TemplateSpec("Claim Document Requested", "Additional documents are required for claim {claimNumber}.", DeliveryChannel.EMAIL));
        map.put(NotificationType.CLAIM_REMINDER,
                new TemplateSpec("Claim Reminder", "Reminder: claim {claimNumber} is still awaiting decision.", DeliveryChannel.EMAIL));
        map.put(NotificationType.CLAIM_ESCALATED,
                new TemplateSpec("Claim Escalated", "Your claim {claimNumber} has been escalated for priority handling.", DeliveryChannel.EMAIL));
        map.put(NotificationType.CLAIM_PAYMENT_INITIATED,
                new TemplateSpec("Claim Payment Initiated", "Payment for claim {claimNumber} has been initiated.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_DETECTED,
                new TemplateSpec("Trigger Detected", "A trigger event was detected for your policy {policyNumber}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_VERIFIED,
                new TemplateSpec("Trigger Verified", "The trigger for policy {policyNumber} has been verified.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_REJECTED,
                new TemplateSpec("Trigger Rejected", "The trigger for policy {policyNumber} was not verified.", DeliveryChannel.EMAIL));
        map.put(NotificationType.TRIGGER_PROCESSED,
                new TemplateSpec("Trigger Processed", "Trigger event {reference} has been processed for policy {policyNumber}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.THRESHOLD_APPROACHING,
                new TemplateSpec("Threshold Approaching", "The {perilName} threshold for your policy {policyNumber} is approaching its trigger level in {zoneName}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_THRESHOLD_EXCEEDED,
                new TemplateSpec("Threshold Exceeded", "The {perilName} threshold of {thresholdValue} {unit} was exceeded in {zoneName} with an observed value of {observedValue} {unit}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_EVENT_DETECTED,
                new TemplateSpec("Trigger Event Detected", "A {perilName} trigger event was detected for your policy {policyNumber} in {zoneName}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_EVENT_VERIFIED,
                new TemplateSpec("Your parametric coverage has been triggered", "A verified {perilName} event was recorded in {zoneName}. The measured value was {observedValue} {unit}, meeting the policy threshold of {thresholdValue} {unit}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_EVENT_REJECTED,
                new TemplateSpec("Trigger Event Rejected", "Trigger event {reference} for policy {policyNumber} was reviewed and not verified.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PARAMETRIC_COVERAGE_TRIGGERED,
                new TemplateSpec("Parametric Coverage Triggered", "Your parametric coverage for policy {policyNumber} has been activated after a verified event.", DeliveryChannel.IN_APP));
        map.put(NotificationType.MANUAL_TRIGGER_CONFIRMED,
                new TemplateSpec("Manual Trigger Confirmed", "The manual trigger review for event {reference} was confirmed.", DeliveryChannel.IN_APP));
        map.put(NotificationType.MANUAL_TRIGGER_NOT_CONFIRMED,
                new TemplateSpec("Manual Trigger Not Confirmed", "The manual trigger review for event {reference} was not confirmed.", DeliveryChannel.EMAIL));
        map.put(NotificationType.OBSERVATION_CREATED,
                new TemplateSpec("Observation Recorded", "A new observation {reference} has been recorded for zone {zoneName}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PERIL_CREATED,
                new TemplateSpec("Peril Defined", "New peril {perilName} has been defined under product {productName}.", DeliveryChannel.IN_APP));
        map.put(NotificationType.TRIGGER_DEFINITION_CREATED,
                new TemplateSpec("Trigger Definition Created", "New trigger definition {triggerName} has been created.", DeliveryChannel.IN_APP));
        map.put(NotificationType.LOCATION_ZONE_CREATED,
                new TemplateSpec("Location Zone Created", "New location zone {zoneName} has been created.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYMENT_SUCCESS,
                new TemplateSpec("Payment Successful", "Payment {reference} of {amount} was successful.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYMENT_FAILED,
                new TemplateSpec("Payment Failed", "Payment {reference} failed. Please check your payment details.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PAYMENT_PENDING,
                new TemplateSpec("Payment Pending", "Your payment {reference} is pending.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYMENT_RETRY,
                new TemplateSpec("Payment Retry", "We are retrying your payment {reference}.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PAYMENT_REFUNDED,
                new TemplateSpec("Payment Refunded", "Your payment {reference} has been refunded.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PAYOUT_PROCESSED,
                new TemplateSpec("Payout Processed", "Payout {reference} of {amount} has been processed.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYOUT_INITIATED,
                new TemplateSpec("Payout Initiated", "The payout of {amount} for claim {claimNumber} has been initiated.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYOUT_SUCCESS,
                new TemplateSpec("Payout completed", "The payout of {amount} for claim {claimNumber} was completed successfully.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYOUT_FAILED,
                new TemplateSpec("Payout Failed", "Payout {reference} failed. Our team will investigate.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PAYOUT_PENDING,
                new TemplateSpec("Payout Pending", "Your payout {reference} is being processed.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PAYOUT_RETRY,
                new TemplateSpec("Payout Retry", "We are retrying payout {reference}.", DeliveryChannel.EMAIL));
        map.put(NotificationType.QUOTE_CREATED,
                new TemplateSpec("Quote Created", "Your quote {quoteNumber} has been created.", DeliveryChannel.IN_APP));
        map.put(NotificationType.QUOTE_EXPIRED,
                new TemplateSpec("Quote Expired", "Your quote {quoteNumber} has expired.", DeliveryChannel.EMAIL));
        map.put(NotificationType.QUOTE_CONSUMED,
                new TemplateSpec("Quote Consumed", "Your quote {quoteNumber} has been used successfully.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PREMIUM_DUE,
                new TemplateSpec("Premium Due", "Your premium for policy {policyNumber} is due on {dueDate}.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PREMIUM_OVERDUE,
                new TemplateSpec("Premium Overdue", "Your premium for policy {policyNumber} is overdue. Please pay at the earliest.", DeliveryChannel.EMAIL));
        map.put(NotificationType.REFUND_PROCESSED,
                new TemplateSpec("Refund Processed", "Your refund {reference} has been processed.", DeliveryChannel.EMAIL));
        map.put(NotificationType.REFUND_FAILED,
                new TemplateSpec("Refund Failed", "Your refund {reference} could not be processed.", DeliveryChannel.EMAIL));
        map.put(NotificationType.RECONCILIATION_COMPLETED,
                new TemplateSpec("Reconciliation Completed", "Reconciliation completed with no discrepancies.", DeliveryChannel.IN_APP));
        map.put(NotificationType.RECONCILIATION_DISCREPANCY,
                new TemplateSpec("Reconciliation Discrepancy", "A discrepancy was found during reconciliation.", DeliveryChannel.EMAIL));
        map.put(NotificationType.PRICING_RULE_CREATED,
                new TemplateSpec("Pricing Rule Created", "New pricing rule {ruleName} has been created.", DeliveryChannel.IN_APP));
        map.put(NotificationType.PRICING_RULE_UPDATED,
                new TemplateSpec("Pricing Rule Updated", "Pricing rule {ruleName} has been updated.", DeliveryChannel.IN_APP));
        map.put(NotificationType.GENERAL,
                new TemplateSpec("Notification", "{message}", DeliveryChannel.IN_APP));
        return map;
    }

    private record TemplateSpec(String title, String message, DeliveryChannel channel) {}
}