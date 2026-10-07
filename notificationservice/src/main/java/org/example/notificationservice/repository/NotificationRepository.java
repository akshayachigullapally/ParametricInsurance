package org.example.notificationservice.repository;

import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    Flux<Notification> findAllByRecipientUserIdOrderByCreatedAtDesc(Long userId);
    Flux<Notification> findAllByRecipientCustomerIdOrderByCreatedAtDesc(Long customerId);
    Flux<Notification> findAllByRecipientRoleOrderByCreatedAtDesc(String role);
    Flux<Notification> findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    Flux<Notification> findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(Long customerId);
    Flux<Notification> findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc(String role);
    Flux<Notification> findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus status);
    Flux<Notification> findAllByOrderByCreatedAtDesc();
    Flux<Notification> findAllByTypeOrderByCreatedAtDesc(NotificationType type);
    Mono<Notification> findFirstByIdempotencyKey(String idempotencyKey);
}
