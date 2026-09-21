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
@Table("default_templates")
public class DefaultTemplate {
    @Id
    private Long id;

    @Column("notification_type")
    private NotificationType notificationType;

    @Column("title_template")
    private String titleTemplate;

    @Column("message_template")
    private String messageTemplate;

    @Column("default_channel")
    private DeliveryChannel defaultChannel;

    @Column("is_active")
    private boolean active;

    @Column("created_at")
    private LocalDateTime createdAt;
}