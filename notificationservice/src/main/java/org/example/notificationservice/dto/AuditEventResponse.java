package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data@Builder@NoArgsConstructor@AllArgsConstructor
public class AuditEventResponse{
    private Long id;
    private String eventReference;
    private String eventType;
    private Long actorUserId; private String actorRole; private String entityType; private Long entityId; private String description; private String correlationId; private LocalDateTime createdAt;}