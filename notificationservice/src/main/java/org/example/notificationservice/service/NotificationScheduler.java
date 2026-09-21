package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.repository.DeliveryAttemptRepository;
import org.example.notificationservice.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class NotificationScheduler {

    private final NotificationRepository repository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final NotificationDeliveryService deliveryService;

    public NotificationScheduler(NotificationRepository repository,
                                 DeliveryAttemptRepository deliveryAttemptRepository,
                                 NotificationDeliveryService deliveryService) {
        this.repository = repository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.deliveryService = deliveryService;
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void retryFailedDeliveries() {
        repository.findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED)
                .filter(n -> n.getRetryCount() < n.getMaxRetries())
                .concatMap(n -> {
                    n.setRetryCount(n.getRetryCount() + 1);
                    return deliveryService.dispatch(n)
                            .flatMap(repository::save);
                })
                .then()
                .subscribe(ignored -> log.debug("Delivery retry pass complete"),
                        error -> log.warn("Delivery retry pass failed: {}", error.getMessage()));
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        repository.findAll()
                .filter(n -> (n.isRead() || n.getDeliveryStatus() == DeliveryStatus.FAILED)
                        && n.getCreatedAt() != null
                        && n.getCreatedAt().isBefore(cutoff))
                .concatMap(repository::delete)
                .thenMany(deliveryAttemptRepository.findAll()
                        .filter(a -> a.getAttemptedAt() != null && a.getAttemptedAt().isBefore(cutoff))
                        .concatMap(deliveryAttemptRepository::delete))
                .subscribe(ignored -> log.info("Notification cleanup pass complete"),
                        error -> log.warn("Notification cleanup pass failed: {}", error.getMessage()));
    }
}