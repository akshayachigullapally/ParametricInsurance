package org.example.notificationservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_preferences")
public class NotificationPreference {
    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("customer_id")
    private Long customerId;

    @Column("notification_type")
    private NotificationType notificationType;

    @Column("in_app_enabled")
    private boolean inAppEnabled;

    @Column("email_enabled")
    private boolean emailEnabled;

    @Column("sms_enabled")
    private boolean smsEnabled;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
