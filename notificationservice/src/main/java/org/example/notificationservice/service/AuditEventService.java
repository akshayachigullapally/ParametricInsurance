package org.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.example.notificationservice.dto.AuditEventResponse;
import org.example.notificationservice.dto.InternalAuditEventRequest;
import org.example.notificationservice.model.AuditEvent;
import org.example.notificationservice.repository.AuditEventRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class AuditEventService {

    private final AuditEventRepository repository;

    public AuditEventService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public Mono<AuditEventResponse> create(InternalAuditEventRequest r) {
        log.info("Creating audit event: type={}, entityType={}, entityId={}",
                r.getEventType(), r.getEntityType(), r.getEntityId());

        AuditEvent e = AuditEvent.builder()
                .eventReference("AUD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                .eventType(r.getEventType().trim())
                .actorUserId(r.getActorUserId())
                .actorRole(r.getActorRole())
                .entityType(r.getEntityType().trim())
                .entityId(r.getEntityId())
                .description(r.getDescription().trim())
                .correlationId(r.getCorrelationId())
                .createdAt(LocalDateTime.now())
                .build();
        return repository.save(e).map(this::response);
    }

    public Flux<AuditEventResponse> all() {
        return all(null, null);
    }

    // Admin view of every user's history, optionally narrowed to one user and/or role.
    public Flux<AuditEventResponse> all(Long actorUserId, String actorRole) {
        String role = normalizeRole(actorRole);
        Flux<AuditEvent> events = Flux.defer(() -> actorUserId != null
                ? repository.findAllByActorUserIdOrderByCreatedAtDesc(actorUserId)
                : repository.findAllByOrderByCreatedAtDesc());

        return SecurityUtil.requireAdmin()
                .thenMany(events)
                .filter(e -> role == null || role.equals(normalizeRole(e.getActorRole())))
                .map(this::response);
    }

    // The signed-in user's own history, whatever their role.
    public Flux<AuditEventResponse> mine() {
        return SecurityUtil.userId()
                .flatMapMany(repository::findAllByActorUserIdOrderByCreatedAtDesc)
                .map(this::response);
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String upper = role.trim().toUpperCase();
        return upper.startsWith("ROLE_") ? upper.substring(5) : upper;
    }

    private AuditEventResponse response(AuditEvent e) {
        return AuditEventResponse.builder()
                .id(e.getId())
                .eventReference(e.getEventReference())
                .eventType(e.getEventType())
                .actorUserId(e.getActorUserId())
                .actorRole(e.getActorRole())
                .entityType(e.getEntityType())
                .entityId(e.getEntityId())
                .description(e.getDescription())
                .correlationId(e.getCorrelationId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
