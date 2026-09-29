package org.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventRequest{
    private String eventType;
    private String entityType;
    private Long entityId;
    private Long actorUserId;
    private String actorRole;
    private String description;
    private String oldValue;
    private String newValue;
}
