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
    private final NotificationStream stream;

    public NotificationService(NotificationRepository repository,
                               NotificationTemplateService templateService,
                               NotificationPreferenceService preferenceService,
                               DeliveryAttemptService deliveryAttemptService,
                               NotificationDeliveryService deliveryService,
                               NotificationStream stream) {
        this.repository = repository;
        this.templateService = templateService;
        this.preferenceService = preferenceService;
        this.deliveryAttemptService = deliveryAttemptService;
        this.deliveryService = deliveryService;
        this.stream = stream;
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
                .doOnNext(stream::publish);
    }

    public Flux<NotificationResponse> createForChannels(InternalNotificationRequest r) {
        log.debug("Creating notification for channels");
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
                .map(this::response)
                .doOnNext(stream::publish);
    }

    public Flux<NotificationResponse> sendFromTemplate(SendFromTemplateRequest r) {
        log.debug("Sending notification from template");
        boolean hasCode = r.getTemplateCode() != null && !r.getTemplateCode().isBlank();
        if (!hasCode && r.getNotificationType() == null) {
            return Flux.error(new ValidationException("templateCode or notificationType is required"));
        }
        if (r.getRecipientUserId() == null && r.getRecipientCustomerId() == null
                && (r.getRecipientRole() == null || r.getRecipientRole().isBlank())) {
            return Flux.error(new ValidationException("At least one recipient identifier is required"));
        }
        Mono<NotificationTemplate> resolved = hasCode
                ? templateService.findByCode(r.getTemplateCode())
                        .switchIfEmpty(Mono.error(new NotFoundException(
                                "No active template found with code " + r.getTemplateCode().trim())))
                : templateService.getDefaultTemplate(r.getNotificationType())
                        .switchIfEmpty(Mono.defer(() -> templateService.findByType(r.getNotificationType())));
        return resolved
                .flatMapMany(template -> {
                    String title = render(template.getTitleTemplate(), r);
                    String message = render(template.getMessageTemplate(), r);
                    InternalNotificationRequest req = InternalNotificationRequest.builder()
                            .recipientUserId(r.getRecipientUserId())
                            .recipientCustomerId(r.getRecipientCustomerId())
                            .recipientRole(r.getRecipientRole())
                            .type(r.getNotificationType() != null
                                    ? r.getNotificationType()
                                    : template.getNotificationType())
                            .title(title)
                            .message(message)
                            .relatedEntityType(r.getRelatedEntityType())
                            .relatedEntityId(r.getRelatedEntityId())
                            .deliveryChannel(r.getDeliveryChannel() != null
                                    ? r.getDeliveryChannel()
                                    : template.getDefaultChannel())
                            .deliveryChannels(r.getDeliveryChannels())
                            .maxRetries(r.getMaxRetries())
                            .build();
                    return createForChannels(req);
                });
    }

    public Flux<NotificationResponse> batch(List<InternalNotificationRequest> requests) {
        log.debug("Creating notification batch");
        if (requests == null || requests.isEmpty()) {
            return Flux.error(new ValidationException("notifications must not be empty"));
        }
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
        log.debug("Fetching my notifications: unreadOnly={}",unreadOnly);
        return unreadOnly ? mine(NotificationQuery.unreadOnly()) : mine(NotificationQuery.defaults());
    }

    /*
     * Producing services (productservice, claimsservice, triggerservice, financialservice)
     * never have a distinct "customer id" separate from the authenticated user's id -
     * they all populate NotificationRequest.recipientCustomerId with the same value as
     * the recipient's userId (see e.g. PolicyHandler using SecurityUtil.getUserId() as
     * the policy's customerId). authservice also never issues a JWT "customerId" claim
     * (AuthService.generateToken(user, null)), so SecurityUtil.customerId() here is
     * always empty. Matching recipientCustomerId against the caller's own userId is
     * therefore the correct - and only working - way to retrieve those notifications.
     */
    public Flux<NotificationResponse> mine(NotificationQuery q) {
        log.debug("Fetching my notifications");
        return SecurityUtil.userId().flatMapMany(userId ->
                SecurityUtil.role().flatMapMany(role -> {
                    Flux<Notification> filtered = visibleTo(userId, role, q.isUnreadOnly())
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
                }));
    }

    /**
     * Every notification the user sees in their inbox: addressed to their user id,
     * to their customer id (producers set it to the user id - see above), or a
     * genuine broadcast to their role (no specific recipient, so it never leaks
     * a notification meant for someone else who shares the role).
     */
    private Flux<Notification> visibleTo(Long userId, String role, boolean unreadOnly) {
        Flux<Notification> roleBroadcasts = (unreadOnly
                ? repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc(role)
                : repository.findAllByRecipientRoleOrderByCreatedAtDesc(role))
                .filter(n -> n.getRecipientUserId() == null && n.getRecipientCustomerId() == null);
        return Flux.concat(
                        unreadOnly
                                ? repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                                : repository.findAllByRecipientUserIdOrderByCreatedAtDesc(userId),
                        unreadOnly
                                ? repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(userId)
                                : repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(userId),
                        roleBroadcasts)
                .distinct(Notification::getId);
    }

        // Stream live notifications filtered for the current authenticated user.
        // See the comment on mine(NotificationQuery) - recipientCustomerId is matched
        // against the caller's own userId, since that is what producers actually set it to.
        public Flux<NotificationResponse> stream() {
            log.debug("Opening notification stream");
        return SecurityUtil.userId().flatMapMany(userId ->
                SecurityUtil.role().flatMapMany(role ->
                    stream.asFlux()
                        .filter(n -> (n.getRecipientUserId() != null && n.getRecipientUserId().equals(userId))
                            || (n.getRecipientCustomerId() != null && n.getRecipientCustomerId().equals(userId))
                            || (n.getRecipientUserId() == null && n.getRecipientCustomerId() == null
                                && n.getRecipientRole() != null && n.getRecipientRole().equals(role)))
                ));
        }

    public Mono<UnreadCountResponse> unreadCount() {
        log.debug("Fetching unread notification count");
        return mine(true).count().map(UnreadCountResponse::new);
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

    /** Marks everything unread in the user's inbox (the same set as mine(true) and unreadCount()). */
    public Mono<MessageResponse> markAllRead() {
        log.debug("Marking all notifications read");
        return SecurityUtil.userId().flatMapMany(userId ->
                        SecurityUtil.role().flatMapMany(role -> visibleTo(userId, role, true)))
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

    /** Admin view of all notifications: mark any notification as read. */
    public Mono<NotificationResponse> adminMarkRead(Long id) {
        log.debug("Admin marking notification read: id={}",id);
        return SecurityUtil.requireAdmin()
                .then(Mono.defer(() -> repository.findById(id)))
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(n -> {
                    if (n.isRead()) {
                        return Mono.just(n);
                    }
                    n.setRead(true);
                    n.setReadAt(LocalDateTime.now());
                    return repository.save(n);
                })
                .map(this::response);
    }

    /** Admin view of all notifications: delete any notification. */
    public Mono<MessageResponse> adminDelete(Long id) {
        return SecurityUtil.requireAdmin()
                .then(Mono.defer(() -> repository.findById(id)))
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(n -> {
                    log.info("Admin deleting notification: id={}", id);
                    return repository.delete(n);
                })
                .thenReturn(new MessageResponse("Notification deleted"));
    }

    public Flux<NotificationResponse> all() {
        log.debug("Fetching all notifications");
        return SecurityUtil.requireAdmin().thenMany(repository.findAllByOrderByCreatedAtDesc())
                .map(this::response);
    }

    public Flux<NotificationResponse> failed() {
        log.debug("Fetching failed notifications");
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED))
                .map(this::response);
    }

    public Flux<NotificationResponse> byType(NotificationType type) {
        log.debug("Fetching notifications by type");
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByTypeOrderByCreatedAtDesc(type))
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
                        SecurityUtil.role().flatMap(role -> {
                            // recipientCustomerId is matched against userId - see mine(NotificationQuery).
                            // Role only grants ownership for genuine broadcasts (no specific recipient).
                            boolean ok = userId.equals(n.getRecipientUserId())
                                    || userId.equals(n.getRecipientCustomerId())
                                    || (n.getRecipientUserId() == null && n.getRecipientCustomerId() == null
                                            && role.equals(n.getRecipientRole()));
                            return ok ? Mono.just(n) : Mono.error(new ForbiddenException("Notification does not belong to current user"));
                        })));
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