package org.example.notificationservice.repository;

import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@ActiveProfiles("test")
@Import(R2dbcTestConfig.class)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll().block();
    }

    private Notification buildNotification(Long userId, Long customerId, String role,
                                           boolean read, DeliveryStatus status, LocalDateTime createdAt) {
        return Notification.builder()
                .notificationReference("NTF-" + System.nanoTime())
                .recipientUserId(userId)
                .recipientCustomerId(customerId)
                .recipientRole(role)
                .type(NotificationType.GENERAL)
                .title("Test Title")
                .message("Test Message")
                .read(read)
                .deliveryChannel(DeliveryChannel.IN_APP)
                .deliveryStatus(status)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildNotification(null, 100L, null, false, DeliveryStatus.DELIVERED, now.minusHours(1))).block();
        repository.save(buildNotification(null, 100L, null, true, DeliveryStatus.DELIVERED, now)).block();

        StepVerifier.create(repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(100L))
                .assertNext(n -> assertThat(n.isRead()).isFalse())
                .verifyComplete();
    }
}
