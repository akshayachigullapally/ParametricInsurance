package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String notificationReference;
    private Long recipientUserId;
    private Long recipientCustomerId;
    private String recipientRole;
    private String type;
    private String title;
    private String message;
    private String relatedEntityType;
    private Long relatedEntityId;
    private Boolean read;
    private LocalDateTime readAt;
    private String deliveryChannel;
    private String deliveryStatus;
    private Integer retryCount;
    private Integer maxRetries;
    private String correlationId;
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}