package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.MessageResponse;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.UnreadCountResponse;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/** In-app notifications: stored for the inbox and pushed live to signed-in users. */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationStream stream;

    public NotificationService(NotificationRepository repository, NotificationStream stream) {
        this.repository = repository;
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

        String idempotencyKey = blankToNull(r.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now();
        Notification n = Notification.builder()
                .notificationReference(ref())
                .recipientUserId(r.getRecipientUserId())
                .recipientCustomerId(r.getRecipientCustomerId())
                .recipientRole(normalizeRole(r.getRecipientRole()))
                .type(r.getType())
                .title(title)
                .message(message)
                .relatedEntityType(r.getRelatedEntityType())
                .relatedEntityId(r.getRelatedEntityId())
                .read(false)
                .correlationId(r.getCorrelationId())
                .idempotencyKey(idempotencyKey)
                .createdAt(now)
                .updatedAt(now)
                .build();

        log.info("Creating notification: type={}, recipientUserId={}, recipientCustomerId={}, role={}",
                r.getType(), r.getRecipientUserId(), r.getRecipientCustomerId(), n.getRecipientRole());

        // A new notification is saved and pushed live; a repeated idempotency key returns the existing one.
        Mono<Notification> existing = idempotencyKey == null
                ? Mono.empty()
                : repository.findFirstByIdempotencyKey(idempotencyKey);
        return existing.map(this::response)
                .switchIfEmpty(Mono.defer(() -> repository.save(n)
                        .map(this::response)
                        .doOnNext(stream::publish)))
                .onErrorResume(DuplicateKeyException.class, e ->
                        repository.findFirstByIdempotencyKey(idempotencyKey).map(this::response));
    }

    /*
     * The other services set recipientCustomerId to the customer's user id (there is no separate
     * customer id), so it is matched against the caller's own user id.
     */
    public Flux<NotificationResponse> mine(boolean unreadOnly) {
        return SecurityUtil.userId().flatMapMany(userId ->
                SecurityUtil.role().flatMapMany(role -> visibleTo(userId, role, unreadOnly)
                        .sort((x, y) -> y.getCreatedAt().compareTo(x.getCreatedAt()))
                        .map(this::response)));
    }

    /**
     * Everything in the user's inbox: addressed to their user id, to their customer id, or a
     * broadcast to their role (no specific recipient, so nobody sees another person's notification).
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

    /** Live notifications for the signed-in user (same recipients as the inbox). */
    public Flux<NotificationResponse> stream() {
        return SecurityUtil.userId().flatMapMany(userId ->
                SecurityUtil.role().flatMapMany(role ->
                        stream.asFlux().filter(n -> isFor(n, userId, role))));
    }

    public Mono<UnreadCountResponse> unreadCount() {
        return mine(true).count().map(UnreadCountResponse::new);
    }

    public Mono<NotificationResponse> markRead(Long id) {
        return owned(id).flatMap(this::markReadAndSave).map(this::response);
    }

    /** Marks everything unread in the user's inbox (the same set as the unread count). */
    public Mono<MessageResponse> markAllRead() {
        return SecurityUtil.userId().flatMapMany(userId ->
                        SecurityUtil.role().flatMapMany(role -> visibleTo(userId, role, true)))
                .flatMap(this::markReadAndSave)
                .count()
                .map(c -> new MessageResponse(c + " notification(s) marked as read"));
    }

    public Mono<MessageResponse> delete(Long id) {
        return owned(id).flatMap(repository::delete)
                .thenReturn(new MessageResponse("Notification deleted"));
    }

    /** Admin: every notification in the system, newest first. */
    public Flux<NotificationResponse> all() {
        return SecurityUtil.requireAdmin()
                .thenMany(repository.findAllByOrderByCreatedAtDesc())
                .map(this::response);
    }

    /** Admin: mark any notification as read. */
    public Mono<NotificationResponse> adminMarkRead(Long id) {
        return SecurityUtil.requireAdmin()
                .then(Mono.defer(() -> repository.findById(id)))
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(this::markReadAndSave)
                .map(this::response);
    }

    /** Admin: delete any notification. */
    public Mono<MessageResponse> adminDelete(Long id) {
        return SecurityUtil.requireAdmin()
                .then(Mono.defer(() -> repository.findById(id)))
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(repository::delete)
                .thenReturn(new MessageResponse("Notification deleted"));
    }

    private Mono<Notification> markReadAndSave(Notification n) {
        if (n.isRead()) {
            return Mono.just(n);
        }
        n.setRead(true);
        n.setReadAt(LocalDateTime.now());
        n.setUpdatedAt(LocalDateTime.now());
        return repository.save(n);
    }

    private Mono<Notification> owned(Long id) {
        return repository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Notification not found")))
                .flatMap(n -> SecurityUtil.userId().flatMap(userId ->
                        SecurityUtil.role().flatMap(role -> isFor(response(n), userId, role)
                                ? Mono.just(n)
                                : Mono.error(new ForbiddenException("Notification does not belong to current user")))));
    }

    private static boolean isFor(NotificationResponse n, Long userId, String role) {
        return userId.equals(n.getRecipientUserId())
                || userId.equals(n.getRecipientCustomerId())
                || (n.getRecipientUserId() == null && n.getRecipientCustomerId() == null
                        && role.equals(n.getRecipientRole()));
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
                .createdAt(n.getCreatedAt())
                .build();
    }

    private static String normalizeRole(String role) {
        return role == null || role.isBlank() ? null : (role.startsWith("ROLE_") ? role : "ROLE_" + role);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String ref() {
        return "NTF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
