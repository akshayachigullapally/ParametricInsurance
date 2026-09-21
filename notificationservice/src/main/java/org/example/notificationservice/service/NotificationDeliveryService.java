package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
public class NotificationDeliveryService {

    private final DeliveryAttemptService attemptService;
    private final EmailDeliveryService emailService;
    private final SmsDeliveryService smsService;

    public NotificationDeliveryService(DeliveryAttemptService attemptService,
                                       EmailDeliveryService emailService,
                                       SmsDeliveryService smsService) {
        this.attemptService = attemptService;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    public Mono<Notification> dispatch(Notification n) {
        DeliveryChannel channel = n.getDeliveryChannel() == null ? DeliveryChannel.IN_APP : n.getDeliveryChannel();
        return switch (channel) {
            case IN_APP -> markDelivered(n);
            case EMAIL -> emailService.sendEmail(recipientLabel(n), n.getTitle(), n.getMessage())
                    .then(markDelivered(n))
                    .onErrorResume(e -> markFailed(n, e));
            case SMS -> smsService.sendSms(recipientLabel(n), n.getMessage())
                    .then(markDelivered(n))
                    .onErrorResume(e -> markFailed(n, e));
        };
    }

    private Mono<Notification> markDelivered(Notification n) {
        n.setDeliveryStatus(DeliveryStatus.DELIVERED);
        n.setUpdatedAt(LocalDateTime.now());
        return attemptService.recordAttempt(n.getId(), n.getDeliveryChannel(), DeliveryStatus.DELIVERED, null)
                .thenReturn(n);
    }

    private Mono<Notification> markFailed(Notification n, Throwable e) {
        log.warn("Delivery failed for notification id={}, channel={}: {}",
                n.getId(), n.getDeliveryChannel(), e.getMessage());
        n.setDeliveryStatus(DeliveryStatus.FAILED);
        n.setUpdatedAt(LocalDateTime.now());
        return attemptService.recordAttempt(
                        n.getId(), n.getDeliveryChannel(), DeliveryStatus.FAILED,
                        e.getMessage() == null ? "Delivery failed" : e.getMessage())
                .thenReturn(n);
    }

    private String recipientLabel(Notification n) {
        if (n.getRecipientCustomerId() != null) return "customer-" + n.getRecipientCustomerId();
        if (n.getRecipientUserId() != null) return "user-" + n.getRecipientUserId();
        return "recipient";
    }
}