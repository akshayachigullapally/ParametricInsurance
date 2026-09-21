package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationType;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateResponse {
    private Long id;
    private String templateCode;
    private NotificationType notificationType;
    private String titleTemplate;
    private String messageTemplate;
    private DeliveryChannel defaultChannel;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
