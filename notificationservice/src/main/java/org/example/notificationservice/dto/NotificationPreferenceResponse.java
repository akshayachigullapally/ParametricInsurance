package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.NotificationType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceResponse {
    private Long id;
    private Long userId;
    private Long customerId;
    private NotificationType notificationType;
    private boolean inAppEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
