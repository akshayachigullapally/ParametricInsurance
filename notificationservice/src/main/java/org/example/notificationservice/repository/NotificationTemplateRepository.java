package org.example.notificationservice.repository;

import org.example.notificationservice.model.NotificationTemplate;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface NotificationTemplateRepository extends ReactiveCrudRepository<NotificationTemplate, Long> {
    // Spring Data R2DBC cannot wrap results in Optional; an empty Mono means "not found".
    Flux<NotificationTemplate> findByNotificationTypeAndActiveTrue(NotificationType type);
    Mono<NotificationTemplate> findByTemplateCodeAndActiveTrue(String templateCode);
}
