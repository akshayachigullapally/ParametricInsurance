package org.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttemptResponse {
    private Long id;
    private Long notificationId;
    private DeliveryChannel deliveryChannel;
    private DeliveryStatus deliveryStatus;
    private int attemptNumber;
    private String errorMessage;
    private LocalDateTime attemptedAt;
    private LocalDateTime completedAt;
}
