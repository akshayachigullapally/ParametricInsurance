package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalNotificationRequest {
    private Long recipientUserId;
    private Long recipientCustomerId;
    private String recipientRole;
    private String type;
    private String notificationType;
    private String title;
    private String message;
    private String relatedEntityType;
    private Long relatedEntityId;
    private String correlationId;
    private String idempotencyKey;
    private String deliveryChannel;
}