package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class NotificationScheduler {

    private final NotificationRepository repository;

    public NotificationScheduler(NotificationRepository repository) {
        this.repository = repository;
    }

    /** Daily: removes read notifications older than 90 days. */
    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        repository.findAll()
                .filter(n -> n.isRead() && n.getCreatedAt() != null && n.getCreatedAt().isBefore(cutoff))
                .concatMap(repository::delete)
                .subscribe(ignored -> { },
                        error -> log.warn("Notification cleanup pass failed: {}", error.getMessage()),
                        () -> log.info("Notification cleanup pass complete"));
    }
}
