package org.example.notificationservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.notificationservice.model.NotificationType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertNotificationPreferenceRequest {
    @NotNull
    private NotificationType notificationType;

    private Boolean inAppEnabled;
    private Boolean emailEnabled;
    private Boolean smsEnabled;
}
