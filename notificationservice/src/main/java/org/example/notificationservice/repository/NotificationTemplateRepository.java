package org.example.notificationservice.repository;

import org.example.notificationservice.model.NotificationTemplate;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.Optional;

public interface NotificationTemplateRepository extends ReactiveCrudRepository<NotificationTemplate, Long> {
    Mono<Optional<NotificationTemplate>> findByNotificationTypeAndActiveTrue(NotificationType type);
    Mono<Optional<NotificationTemplate>> findByTemplateCodeAndActiveTrue(String templateCode);
}
