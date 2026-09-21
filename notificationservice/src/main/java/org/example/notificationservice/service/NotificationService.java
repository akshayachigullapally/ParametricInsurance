package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.MessageResponse;
import org.example.notificationservice.dto.NotificationQuery;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.SendFromTemplateRequest;
import org.example.notificationservice.dto.UnreadCountResponse;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.*;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationTemplateService templateService;
    private final NotificationPreferenceService preferenceService;
    private final DeliveryAttemptService deliveryAttemptService;
    private final NotificationDeliveryService deliveryService;
    private final Sinks.Many<NotificationResponse> notificationSink = Sinks.many().multicast().directBestEffort();

    public NotificationService(NotificationRepository repository,
                               NotificationTemplateService templateService,
                               NotificationPreferenceService preferenceService,
                               DeliveryAttemptService deliveryAttemptService,
                               NotificationDeliveryService deliveryService) {
        this.repository = repository;
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.deliveryAttemptService = deliveryAttemptService;
        this.deliveryService = deliveryService;
    }

    public Mono<NotificationResponse> create(InternalNotificationRequest r) {
        if (r.getRecipientUserId() == null && r.getRecipientCustomerId() == null
                && (r.getRecipientRole() == null || r.getRecipientRole().isBlank())) {
            return Mono.error(new ValidationException("At least one recipient identifier is required"));
        }
        if (r.getRelatedEntityType() == null && r.getRelatedEntityId() != null) {
            return Mono.error(new ValidationException("relatedEntityType is required when relatedEntityId is supplied"));
        }

        String title = r.getTitle() != null ? r.getTitle().trim() : "";
        String message = r.getMessage() != null ? r.getMessage().trim() : "";

        if (title.isBlank()) {
            return Mono.error(new ValidationException("title must not be blank"));
        }
        if (message.isBlank()) {
            return Mono.error(new ValidationException("message must not be blank"));
        }

        DeliveryChannel channel = r.getDeliveryChannel() == null ? DeliveryChannel.IN_APP : r.getDeliveryChannel();
        String idempotencyKey = blankToNull(r.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now();

        Notification n = Notification.builder()
                .notificationReference(ref("NTF"))
                .recipientUserId(r.getRecipientUserId())
                .recipientCustomerId(r.getRecipientCustomerId())
                .recipientRole(normalizeRole(r.getRecipientRole()))
                .type(r.getType())
                .title(title)
                .message(message)
                .relatedEntityType(r.getRelatedEntityType())
                .relatedEntityId(r.getRelatedEntityId())
                .read(false)
                .deliveryChannel(channel)
                .deliveryStatus(DeliveryStatus.CREATED)
                .correlationId(r.getCorrelationId())
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        log.info("Creating notification: type={}, recipientUserId={}, recipientCustomerId={}, channel={}, idempotencyPresent={}",
                r.getType(), r.getRecipientUserId(), r.getRecipientCustomerId(), channel, idempotencyKey != null);

        return persistUnique(idempotencyKey, n)
                .flatMap(saved -> {
                    if (isNew(saved)) {
                        return deliveryAttemptService.recordAttempt(saved.getId(), channel, DeliveryStatus.DELIVERED, null)
                                .thenReturn(saved)
                                .map(f -> {
                                    f.setDeliveryStatus(DeliveryStatus.DELIVERED);
                                    f.setUpdatedAt(LocalDateTime.now());
                                    return f;
                                })
                                .flatMap(repository::save);
                    }
                    log.info("Duplicate idempotency key detected, returning existing notification: id={}", saved.getId());
                    return Mono.just(saved);
                })
                .map(this::response)
                .doOnNext(this::publish);
    }

    // Publish notification to SSE sink after creation
    private void publish(NotificationResponse response) {
        try {
            notificationSink.tryEmitNext(response);
        } catch (Exception ignored) {
        }
    }

    public Flux<NotificationResponse> createForChannels(InternalNotificationRequest r) {
        validateRequest(r);
        List<DeliveryChannel> channels = resolveChannels(r);
        int maxRetries = r.getMaxRetries() == null ? 3 : r.getMaxRetries();
        return Flux.fromIterable(channels)
                .concatMap(channel -> createAndDispatch(r, channel, maxRetries));
    }

    private Mono<NotificationResponse> createAndDispatch(InternalNotificationRequest r, DeliveryChannel channel, int maxRetries) {
        String title = r.getTitle().trim();
        String message = r.getMessage().trim();
        String idempotencyKey = blankToNull(r.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now();
        Notification n = Notification.builder()
                .notificationReference(ref("NTF"))
                .recipientUserId(r.getRecipientUserId())
                .recipientCustomerId(r.getRecipientCustomerId())
                .recipientRole(normalizeRole(r.getRecipientRole()))
                .type(r.getType())
                .title(title)
                .message(message)
                .relatedEntityType(r.getRelatedEntityType())
                .relatedEntityId(r.getRelatedEntityId())
                .read(false)
                .deliveryChannel(channel)
                .deliveryStatus(DeliveryStatus.CREATED)
                .retryCount(0)
                .maxRetries(maxRetries)
                .correlationId(r.getCorrelationId())
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        log.info("Creating notification: type={}, channel={}, recipientUserId={}, idempotencyPresent={}",
                r.getType(), channel, r.getRecipientUserId(), idempotencyKey != null);

        return persistUnique(idempotencyKey, n)
                .flatMap(persisted -> {
                    if (isNew(persisted)) {
                        return deliveryService.dispatch(persisted)
                                .flatMap(repository::save);
                    }
                    log.info("Duplicate idempotency key detected, returning existing notification: id={}", persisted.getId());
                    return Mono.just(persisted);
                })
                .map(this::response);
    }

    public Flux<NotificationResponse> sendFromTemplate(SendFromTemplateRequest r) {
        if (r.getNotificationType() == null) {
            return Flux.error(new ValidationException("notificationType is required"));
        }
        if (r.getRecipientUserId() == null && r.getRecipientCustomerId() == null
                && (r.getRecipientRole() == null || r.getRecipientRole().isBlank())) {
            return Flux.error(new ValidationException("At least one recipient identifier is required"));
        }
        return templateService.getDefaultTemplate(r.getNotificationType())
                .switchIfEmpty(Mono.defer(() -> templateService.findByType(r.getNotificationType())))
                .flatMapMany(template -> {
                    String title = render(template.getTitleTemplate(), r);
                    String message = render(template.getMessageTemplate(), r);
                    InternalNotificationRequest req = InternalNotificationRequest.builder()
                            .recipientUserId(r.getRecipientUserId())
                            .recipientCustomerId(r.getRecipientCustomerId())
                            .recipientRole(r.getRecipientRole())
                            .type(r.getNotificationType())
                            .title(title)
                            .message(message)
                            .relatedEntityType(r.getRelatedEntityType())
                            .relatedEntityId(r.getRelatedEntityId())
                            .deliveryChannel(r.getDeliveryChannel())
                            .deliveryChannels(r.getDeliveryChannels())
                            .maxRetries(r.getMaxRetries())
                            .build();
                    return createForChannels(req);
                });
    }

    public Flux<NotificationResponse> batch(List<InternalNotificationRequest> requests) {
        return Flux.fromIterable(requests)
                .flatMap(this::createForChannels);
    }

    private void validateRequest(InternalNotificationRequest r) {
        if (r.getRecipientUserId() == null && r.getRecipientCustomerId() == null
                && (r.getRecipientRole() == null || r.getRecipientRole().isBlank())) {
            throw new ValidationException("At least one recipient identifier is required");
        }
        if (r.getRelatedEntityType() == null && r.getRelatedEntityId() != null) {
            throw new ValidationException("relatedEntityType is required when relatedEntityId is supplied");
        }
        if (r.getTitle() == null || r.getTitle().trim().isBlank()) {
            throw new ValidationException("title must not be blank");
        }
        if (r.getMessage() == null || r.getMessage().trim().isBlank()) {
            throw new ValidationException("message must not be blank");
        }
    }

    private List<DeliveryChannel> resolveChannels(InternalNotificationRequest r) {
        if (r.getDeliveryChannels() != null && !r.getDeliveryChannels().isEmpty()) {
            return r.getDeliveryChannels().stream().distinct().toList();
        }
        DeliveryChannel single = r.getDeliveryChannel() == null ? DeliveryChannel.IN_APP : r.getDeliveryChannel();
        return List.of(single);
    }

    private String render(String template, SendFromTemplateRequest r) {
        if (template == null) return "";
        String out = template;
        Map<String, String> variables = r.getVariables() == null ? Map.of() : r.getVariables();
        for (Map.Entry<String, String> e : variables.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue();
            out = out.replace("${" + e.getKey() + "}", value)
                    .replace("{" + e.getKey() + "}", value);
        }
        return out.replaceAll("\\$?\\{[a-zA-Z][a-zA-Z0-9_]*}", "");
    }

    public Flux<NotificationResponse> mine(boolean unreadOnly) {
        return unreadOnly ? mine(NotificationQuery.unreadOnly()) : mine(NotificationQuery.defaults());
    }

    public Flux<NotificationResponse> mine(NotificationQuery q) {
        return SecurityUtil.userId().flatMapMany(userId ->
                SecurityUtil.customerId().defaultIfEmpty(-1L).flatMapMany(customerId ->
                        SecurityUtil.role().flatMapMany(role -> {
Flux<Notification> roleQuery = customerId > 0
                            ? Flux.empty()
                            : q.isUnreadOnly()
                                    ? repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc(role)
                                    : repository.findAllByRecipientRoleOrderByCreatedAtDesc(role);
                    Flux<Notification> all = Flux.concat(
                            q.isUnreadOnly()
                                    ? repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                                    : repository.findAllByRecipientUserIdOrderByCreatedAtDesc(userId),
                            customerId > 0
                                    ? (q.isUnreadOnly()
                                            ? repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(customerId)
                                            : repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(customerId))
                                    : Flux.empty(),
                            roleQuery
                    );
                            Flux<Notification> filtered = all
                                    .distinct(Notification::getId)
                                    .filter(n -> q.getNotificationType() == null || n.getType() == q.getNotificationType())
                                    .filter(n -> q.getCreatedAfter() == null
                                            || (n.getCreatedAt() != null && n.getCreatedAt().isAfter(q.getCreatedAfter())))
                                    .sort((x, y) -> q.isSortAsc()
                                            ? x.getCreatedAt().compareTo(y.getCreatedAt())
                                            : y.getCreatedAt().compareTo(x.getCreatedAt()));
                            Flux<Notification> paged = q.isPaginated()
                                    ? filtered.skip((long) q.getPage() * q.getSize()).take(q.getSize())
                                    : filtered;
                            return paged.map(this::response);
                        })));
    }

        // Stream notifications for the current authenticated user
        public Flux<NotificationResponse> streamForCurrentUser() {
        // merge persisted mine() (recent/history) with live sink events filtered by recipient
        return SecurityUtil.userId().flatMapMany(userId ->
            SecurityUtil.customerId().defaultIfEmpty(-1L).flatMapMany(customerId ->
                SecurityUtil.role().flatMapMany(role ->
                    notificationSink.asFlux()
                        .filter(n -> (n.getRecipientUserId() != null && n.getRecipientUserId().equals(userId))
                            || (n.getRecipientCustomerId() != null && customerId > 0 && n.getRecipientCustomerId().equals(customerId))
                            || (n.getRecipientRole() != null && n.getRecipientRole().equals(role)))
                )));
        }

    public Mono<UnreadCountResponse> unreadCount() {
        return mine(true).count().map(UnreadCountResponse::new);
    }

    // Ensure publishes for createFromTemplate and createForChannels also notify sink
    public Flux<NotificationResponse> createForChannelsAndPublish(InternalNotificationRequest r) {
        return createForChannels(r).doOnNext(this::publish);
    }

    public Mono<NotificationResponse> markRead(Long id) {
        return owned(id).flatMap(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                n.setReadAt(LocalDateTime.now());
                log.info("Marking notification as read: id={}", id);
            }
            return repository.save(n);
        }).map(this::response);
    }

    public Mono<MessageResponse> markAllRead() {
        return SecurityUtil.userId().flatMapMany(userId ->
                repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(userId))
                .flatMap(n -> {
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    return repository.save(n);
                })
                .count()
                .map(c -> new MessageResponse(c + " notification(s) marked as read"));
    }

    public Mono<MessageResponse> delete(Long id) {
        return owned(id).flatMap(n -> {
            log.info("Deleting notification: id={}", id);
            return repository.delete(n);
        }).thenReturn(new MessageResponse("Notification deleted"));
    }

    public Flux<NotificationResponse> all() {
        return SecurityUtil.requireAdmin().thenMany(repository.findAllByOrderByCreatedAtDesc())
                .map(this::response);
    }

    public Flux<NotificationResponse> allPage(int page, int size) {
        return SecurityUtil.requireAdmin().thenMany(repository.findAllByOrderByCreatedAtDesc())
                .skip((long) page * size)
                .take(size)
                .map(this::response);
    }

    public Flux<NotificationResponse> failed() {
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED))
                .map(this::response);
    }

    public Flux<NotificationResponse> byType(NotificationType type) {
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByTypeOrderByCreatedAtDesc(type))
                .map(this::response);
    }

    public Flux<NotificationResponse> byTypePage(NotificationType type, int page, int size) {
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByTypeOrderByCreatedAtDesc(type))
                .skip((long) page * size)
                .take(size)
                .map(this::response);
    }

    private Mono<Notification> persistUnique(String idempotencyKey, Notification n) {
        if (idempotencyKey == null) {
            return repository.save(n);
        }
        return repository.findFirstByIdempotencyKey(idempotencyKey)
                .switchIfEmpty(Mono.defer(() -> repository.save(n)))
                .onErrorResume(DuplicateKeyException.class, e ->
                        repository.findFirstByIdempotencyKey(idempotencyKey));
    }

    private boolean isNew(Notification n) {
        return n.getDeliveryStatus() == DeliveryStatus.CREATED;
    }

    private Mono<Notification> owned(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(n -> SecurityUtil.userId().flatMap(userId ->
                        SecurityUtil.customerId().defaultIfEmpty(-1L).flatMap(customerId ->
                                SecurityUtil.role().flatMap(role -> {
                                    boolean ok = userId.equals(n.getRecipientUserId())
                                            || customerId.equals(n.getRecipientCustomerId())
                                            || role.equals(n.getRecipientRole());
                                    return ok ? Mono.just(n) : Mono.error(new ForbiddenException("Notification does not belong to current user"));
                                }))));
    }

    private NotificationResponse response(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .notificationReference(n.getNotificationReference())
                .recipientUserId(n.getRecipientUserId())
                .recipientCustomerId(n.getRecipientCustomerId())
                .recipientRole(n.getRecipientRole())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(n.getRelatedEntityId())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .deliveryChannel(n.getDeliveryChannel())
                .deliveryStatus(n.getDeliveryStatus())
                .retryCount(n.getRetryCount())
                .maxRetries(n.getMaxRetries())
                .correlationId(n.getCorrelationId())
                .idempotencyKey(n.getIdempotencyKey())
                .createdAt(n.getCreatedAt())
                .updatedAt(n.getUpdatedAt())
                .build();
    }

    private String normalizeRole(String r) {
        return r == null || r.isBlank() ? null : (r.startsWith("ROLE_") ? r : "ROLE_" + r);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String ref(String p) {
        return p + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}