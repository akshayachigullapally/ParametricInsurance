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
@Table("delivery_attempts")
public class DeliveryAttempt {
    @Id
    private Long id;

    @Column("notification_id")
    private Long notificationId;

    @Column("delivery_channel")
    private DeliveryChannel deliveryChannel;

    @Column("delivery_status")
    private DeliveryStatus deliveryStatus;

    @Column("attempt_number")
    private int attemptNumber;

    @Column("error_message")
    private String errorMessage;

    @Column("attempted_at")
    private LocalDateTime attemptedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;
}
