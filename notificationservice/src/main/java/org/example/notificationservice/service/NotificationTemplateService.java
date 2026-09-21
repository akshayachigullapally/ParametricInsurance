package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.CreateNotificationTemplateRequest;
import org.example.notificationservice.dto.NotificationTemplateResponse;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.model.DefaultTemplate;
import org.example.notificationservice.model.NotificationTemplate;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationTemplateDataRepository;
import org.example.notificationservice.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
public class NotificationTemplateService {

    private final NotificationTemplateRepository repository;
    private final NotificationTemplateDataRepository dataRepository;

    public NotificationTemplateService(NotificationTemplateRepository repository,
                                       NotificationTemplateDataRepository dataRepository) {
        this.repository = repository;
        this.dataRepository = dataRepository;
    }

    public Mono<NotificationTemplateResponse> create(CreateNotificationTemplateRequest r) {
        log.info("Creating notification template: code={}, type={}", r.getTemplateCode(), r.getNotificationType());
        NotificationTemplate t = NotificationTemplate.builder()
                .templateCode(r.getTemplateCode().trim())
                .notificationType(r.getNotificationType())
                .titleTemplate(r.getTitleTemplate().trim())
                .messageTemplate(r.getMessageTemplate().trim())
                .defaultChannel(r.getDefaultChannel() != null ? r.getDefaultChannel() : org.example.notificationservice.model.DeliveryChannel.IN_APP)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(t).map(this::response);
    }

    public Mono<NotificationTemplateResponse> update(Long id, CreateNotificationTemplateRequest r) {
        log.info("Updating notification template: id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Template not found")))
                .flatMap(t -> {
                    t.setTemplateCode(r.getTemplateCode().trim());
                    t.setNotificationType(r.getNotificationType());
                    t.setTitleTemplate(r.getTitleTemplate().trim());
                    t.setMessageTemplate(r.getMessageTemplate().trim());
                    if (r.getDefaultChannel() != null) t.setDefaultChannel(r.getDefaultChannel());
                    t.setUpdatedAt(LocalDateTime.now());
                    return repository.save(t);
                })
                .map(this::response);
    }

    public Mono<NotificationTemplateResponse> get(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Template not found")))
                .map(this::response);
    }

    public Flux<NotificationTemplateResponse> all() {
        return repository.findAll().map(this::response);
    }

    public Mono<Void> deactivate(Long id) {
        log.info("Deactivating notification template: id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Template not found")))
                .flatMap(t -> {
                    t.setActive(false);
                    t.setUpdatedAt(LocalDateTime.now());
                    return repository.save(t);
                })
                .then();
    }

    public Mono<Void> activate(Long id) {
        log.info("Activating notification template: id={}", id);
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Template not found")))
                .flatMap(t -> {
                    t.setActive(true);
                    t.setUpdatedAt(LocalDateTime.now());
                    return repository.save(t);
                })
                .then();
    }

    public Flux<NotificationTemplateResponse> defaults() {
        return dataRepository.findAll().map(this::response);
    }

    public Mono<NotificationTemplate> getDefaultTemplate(NotificationType type) {
        return dataRepository.findByNotificationType(type)
                .map(dt -> NotificationTemplate.builder()
                        .templateCode(dt.getNotificationType().name())
                        .notificationType(dt.getNotificationType())
                        .titleTemplate(dt.getTitleTemplate())
                        .messageTemplate(dt.getMessageTemplate())
                        .defaultChannel(dt.getDefaultChannel())
                        .active(true)
                        .build());
    }

    public Mono<NotificationTemplate> findByType(NotificationType type) {
        return repository.findByNotificationTypeAndActiveTrue(type)
                .flatMap(opt -> opt.isPresent() ? Mono.just(opt.get()) : Mono.empty());
    }

    private NotificationTemplateResponse response(DefaultTemplate t) {
        return NotificationTemplateResponse.builder()
                .id(t.getId())
                .templateCode(t.getNotificationType().name())
                .notificationType(t.getNotificationType())
                .titleTemplate(t.getTitleTemplate())
                .messageTemplate(t.getMessageTemplate())
                .defaultChannel(t.getDefaultChannel())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private NotificationTemplateResponse response(NotificationTemplate t) {
        return NotificationTemplateResponse.builder()
                .id(t.getId())
                .templateCode(t.getTemplateCode())
                .notificationType(t.getNotificationType())
                .titleTemplate(t.getTitleTemplate())
                .messageTemplate(t.getMessageTemplate())
                .defaultChannel(t.getDefaultChannel())
                .active(t.isActive())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
