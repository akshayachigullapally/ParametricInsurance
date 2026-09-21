package org.example.notificationservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data@Builder@NoArgsConstructor@AllArgsConstructor@Table("audit_events")
public class AuditEvent{
@Id private Long id;
@Column("event_reference") private String eventReference;
@Column("event_type") private String eventType;
@Column("actor_user_id") private Long actorUserId;
@Column("actor_role") private String actorRole;
@Column("entity_type") private String entityType;
@Column("entity_id") private Long entityId;
private String description;
@Column("correlation_id") private String correlationId;
@Column("created_at") private LocalDateTime createdAt;
}