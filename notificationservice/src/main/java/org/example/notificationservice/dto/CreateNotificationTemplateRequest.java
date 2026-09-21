package org.example.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.NotificationType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationTemplateRequest {
    @NotBlank
    @Size(max = 50)
    private String templateCode;

    @NotNull
    private NotificationType notificationType;

    @NotBlank
    @Size(max = 150)
    private String titleTemplate;

    @NotBlank
    @Size(max = 1000)
    private String messageTemplate;

    private DeliveryChannel defaultChannel;
}
