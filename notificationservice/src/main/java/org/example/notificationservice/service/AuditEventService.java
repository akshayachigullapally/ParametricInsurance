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
        return SecurityUtil.requireAdmin().thenMany(repository.findAllByOrderByCreatedAtDesc())
                .map(this::response);
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
