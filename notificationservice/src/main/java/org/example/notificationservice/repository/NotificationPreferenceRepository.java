package org.example.notificationservice.repository;

import org.example.notificationservice.model.NotificationPreference;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface NotificationPreferenceRepository extends ReactiveCrudRepository<NotificationPreference, Long> {
    Mono<NotificationPreference> findByUserIdAndNotificationType(Long userId, NotificationType type);
    Mono<NotificationPreference> findByCustomerIdAndNotificationType(Long customerId, NotificationType type);
}
