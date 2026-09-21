package org.example.notificationservice.repository;

import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, Long> {
    Flux<Notification> findAllByRecipientUserIdOrderByCreatedAtDesc(Long userId);
    Flux<Notification> findAllByRecipientCustomerIdOrderByCreatedAtDesc(Long customerId);
    Flux<Notification> findAllByRecipientRoleOrderByCreatedAtDesc(String role);
    Flux<Notification> findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    Flux<Notification> findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(Long customerId);
    Flux<Notification> findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc(String role);
    Mono<Long> countByRecipientUserIdAndReadFalse(Long userId);
    Mono<Long> countByRecipientCustomerIdAndReadFalse(Long customerId);
    Mono<Long> countByRecipientRoleAndReadFalse(String role);
    Flux<Notification> findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus status);
    Flux<Notification> findAllByDeliveryStatusInOrderByCreatedAtAsc(List<DeliveryStatus> statuses);
    Flux<Notification> findAllByOrderByCreatedAtDesc();
    Flux<Notification> findAllByTypeOrderByCreatedAtDesc(NotificationType type);
    Flux<Notification> findAllByDeliveryStatusAndRetryCountLessThanOrderByCreatedAtAsc(DeliveryStatus status, int retryCount);
    Mono<Notification> findFirstByIdempotencyKey(String idempotencyKey);
}
