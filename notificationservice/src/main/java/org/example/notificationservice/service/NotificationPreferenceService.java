package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.NotificationPreferenceResponse;
import org.example.notificationservice.dto.UpsertNotificationPreferenceRequest;
import org.example.notificationservice.model.NotificationPreference;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationPreferenceRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    public Mono<NotificationPreferenceResponse> upsert(UpsertNotificationPreferenceRequest r) {
        return SecurityUtil.userId().flatMap(userId ->
                SecurityUtil.customerId().defaultIfEmpty(-1L).flatMap(customerId -> {
                    log.info("Upserting notification preference: userId={}, type={}", userId, r.getNotificationType());
                    return repository.findByUserIdAndNotificationType(userId, r.getNotificationType())
                            .switchIfEmpty(Mono.defer(() -> {
                                NotificationPreference pref = NotificationPreference.builder()
                                        .userId(userId)
                                        .customerId(customerId > 0 ? customerId : null)
                                        .notificationType(r.getNotificationType())
                                        .inAppEnabled(r.getInAppEnabled() != null ? r.getInAppEnabled() : true)
                                        .emailEnabled(r.getEmailEnabled() != null ? r.getEmailEnabled() : false)
                                        .smsEnabled(r.getSmsEnabled() != null ? r.getSmsEnabled() : false)
                                        .createdAt(LocalDateTime.now())
                                        .build();
                                return repository.save(pref);
                            }))
                            .flatMap(existing -> {
                                if (r.getInAppEnabled() != null) existing.setInAppEnabled(r.getInAppEnabled());
                                if (r.getEmailEnabled() != null) existing.setEmailEnabled(r.getEmailEnabled());
                                if (r.getSmsEnabled() != null) existing.setSmsEnabled(r.getSmsEnabled());
                                existing.setUpdatedAt(LocalDateTime.now());
                                return repository.save(existing);
                            })
                            .map(this::response);
                }));
    }

    public Flux<NotificationPreferenceResponse> myPreferences() {
        log.debug("Fetching my notification preferences");
        return SecurityUtil.userId().flatMapMany(userId ->
                repository.findAll().filter(p -> userId.equals(p.getUserId())))
                .map(this::response);
    }

    public Mono<Boolean> isEnabled(Long userId, NotificationType type, String channel) {
        log.debug("Checking notification channel enabled: userId={}, channel={}",userId,channel);
        return repository.findByUserIdAndNotificationType(userId, type)
                .map(p -> {
                    return switch (channel) {
                        case "IN_APP" -> p.isInAppEnabled();
                        case "EMAIL" -> p.isEmailEnabled();
                        case "SMS" -> p.isSmsEnabled();
                        default -> true;
                    };
                })
                .defaultIfEmpty(true);
    }

    private NotificationPreferenceResponse response(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .customerId(p.getCustomerId())
                .notificationType(p.getNotificationType())
                .inAppEnabled(p.isInAppEnabled())
                .emailEnabled(p.isEmailEnabled())
                .smsEnabled(p.isSmsEnabled())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
