package org.example.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data@Builder@NoArgsConstructor@AllArgsConstructor
public class InternalAuditEventRequest{
@NotBlank@Size(max=100) private String eventType;
private Long actorUserId;
private String actorRole;
@NotBlank@Size(max=50) private String entityType;
private Long entityId;
@NotBlank@Size(max=1000) private String description;
@Size(max=100) private String correlationId;
}