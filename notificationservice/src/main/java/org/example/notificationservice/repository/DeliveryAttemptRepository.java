package org.example.notificationservice.repository;

import org.example.notificationservice.model.DeliveryAttempt;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface DeliveryAttemptRepository extends ReactiveCrudRepository<DeliveryAttempt, Long> {
    Flux<DeliveryAttempt> findByNotificationIdOrderByAttemptNumberDesc(Long notificationId);
}
