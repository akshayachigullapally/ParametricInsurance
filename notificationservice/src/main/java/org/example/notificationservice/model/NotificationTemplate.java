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
@Table("notification_templates")
public class NotificationTemplate {
    @Id
    private Long id;

    @Column("template_code")
    private String templateCode;

    @Column("notification_type")
    private NotificationType notificationType;

    private String titleTemplate;

    private String messageTemplate;

    @Column("default_channel")
    private DeliveryChannel defaultChannel;

    @Column("is_active")
    private boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
