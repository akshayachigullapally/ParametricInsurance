package org.example.notificationservice.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.model.RelatedEntityType;

import java.util.List;

@Data@Builder@NoArgsConstructor@AllArgsConstructor
public class InternalNotificationRequest{
@Positive(message="Recipient user id must be positive")
private Long recipientUserId;
@Positive(message="Recipient customer id must be positive")
private Long recipientCustomerId;
@Size(max=50,message="Recipient role must not exceed 50 characters")
private String recipientRole;
@NotNull private NotificationType type;
@NotBlank@Size(max=150) private String title;
@NotBlank@Size(max=1000) private String message;
private RelatedEntityType relatedEntityType;
private Long relatedEntityId;
private DeliveryChannel deliveryChannel;
private List<DeliveryChannel> deliveryChannels;
@Min(value=0,message="Max retries cannot be negative")
@Max(value=10,message="Max retries cannot exceed 10")
private Integer maxRetries;
@Size(max=100) private String correlationId;
@Size(max=255) private String idempotencyKey;
}