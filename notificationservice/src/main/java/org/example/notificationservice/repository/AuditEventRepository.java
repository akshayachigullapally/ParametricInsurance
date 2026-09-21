package org.example.notificationservice.repository;
import org.example.notificationservice.model.AuditEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
public interface AuditEventRepository extends ReactiveCrudRepository<AuditEvent,Long>{Flux<AuditEvent> findAllByOrderByCreatedAtDesc();}