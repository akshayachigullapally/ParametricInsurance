package org.example.notificationservice.repository;

import org.example.notificationservice.model.DefaultTemplate;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface NotificationTemplateDataRepository extends ReactiveCrudRepository<DefaultTemplate, Long> {
    Mono<DefaultTemplate> findByNotificationType(NotificationType type);
}