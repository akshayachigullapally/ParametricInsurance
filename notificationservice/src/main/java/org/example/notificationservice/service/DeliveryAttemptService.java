package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.DeliveryAttemptResponse;
import org.example.notificationservice.model.DeliveryAttempt;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.repository.DeliveryAttemptRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DeliveryAttemptService {

    private final DeliveryAttemptRepository repository;

    public DeliveryAttemptService(DeliveryAttemptRepository repository) {
        this.repository = repository;
    }

    public Mono<DeliveryAttempt> recordAttempt(Long notificationId, DeliveryChannel channel, DeliveryStatus status, String errorMessage) {
        log.info("Recording delivery attempt: notificationId={}, channel={}, status={}", notificationId, channel, status);
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .notificationId(notificationId)
                .deliveryChannel(channel)
                .deliveryStatus(status)
                .attemptNumber(1)
                .errorMessage(errorMessage)
                .attemptedAt(LocalDateTime.now())
                .completedAt(status == DeliveryStatus.DELIVERED || status == DeliveryStatus.FAILED ? LocalDateTime.now() : null)
                .build();
        return repository.save(attempt);
    }

    public Flux<DeliveryAttemptResponse> getByNotificationId(Long notificationId) {
        return repository.findByNotificationIdOrderByAttemptNumberDesc(notificationId)
                .map(this::response);
    }

    private DeliveryAttemptResponse response(DeliveryAttempt a) {
        return DeliveryAttemptResponse.builder()
                .id(a.getId())
                .notificationId(a.getNotificationId())
                .deliveryChannel(a.getDeliveryChannel())
                .deliveryStatus(a.getDeliveryStatus())
                .attemptNumber(a.getAttemptNumber())
                .errorMessage(a.getErrorMessage())
                .attemptedAt(a.getAttemptedAt())
                .completedAt(a.getCompletedAt())
                .build();
    }
}
