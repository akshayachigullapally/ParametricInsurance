package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.model.RelatedEntityType;

import java.time.LocalDateTime;

@Data@Builder@NoArgsConstructor@AllArgsConstructor
public class NotificationResponse{
private Long id; private String notificationReference; private Long recipientUserId; private Long recipientCustomerId; private String recipientRole; private NotificationType type; private String title; private String message; private RelatedEntityType relatedEntityType; private Long relatedEntityId; private boolean read; private LocalDateTime readAt; private DeliveryChannel deliveryChannel; private DeliveryStatus deliveryStatus; private int retryCount; private int maxRetries; private String correlationId; private String idempotencyKey; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}