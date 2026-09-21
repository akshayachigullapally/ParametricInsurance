package org.example.notificationservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data@Builder@NoArgsConstructor@AllArgsConstructor@Table("notifications")
public class Notification{
@Id private Long id;
@Column("notification_reference") private String notificationReference;
@Column("recipient_user_id") private Long recipientUserId;
@Column("recipient_customer_id") private Long recipientCustomerId;
@Column("recipient_role") private String recipientRole;
private NotificationType type;
private String title;
private String message;
@Column("related_entity_type") private RelatedEntityType relatedEntityType;
@Column("related_entity_id") private Long relatedEntityId;
@Column("is_read") private boolean read;
    @Column("read_at") private LocalDateTime readAt;
    @Column("delivery_channel") private DeliveryChannel deliveryChannel;
    @Column("delivery_status") private DeliveryStatus deliveryStatus;
    @Column("retry_count") private int retryCount;
    @Builder.Default @Column("max_retries") private int maxRetries = 3;
    @Column("correlation_id") private String correlationId;
    @Column("idempotency_key") private String idempotencyKey;
    @Column("created_at") private LocalDateTime createdAt;
    @Column("updated_at") private LocalDateTime updatedAt;
}