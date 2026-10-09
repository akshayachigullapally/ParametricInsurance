package org.example.notificationservice.repository;

import org.example.notificationservice.model.Notification;
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
    Flux<Notification> findAllByOrderByCreatedAtDesc();
    Mono<Notification> findFirstByIdempotencyKey(String idempotencyKey);
}
