package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventRequest {
    private String eventType;
    private String entityType;
    private Long entityId;
    private Long actorUserId;
    private String actorRole;
    private String description;
    private String oldValue;
    private String newValue;
    private LocalDateTime timestamp;
}