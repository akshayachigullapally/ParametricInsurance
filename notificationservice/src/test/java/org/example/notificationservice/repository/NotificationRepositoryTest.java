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

    @Test
    void shouldSaveAndFindById() {
        Notification notification = buildNotification(1L, 100L, "ROLE_USER",
                false, DeliveryStatus.DELIVERED, LocalDateTime.now());

        Notification saved = repository.save(notification).block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNotificationReference()).startsWith("NTF-");

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(n -> {
                    assertThat(n.getRecipientUserId()).isEqualTo(1L);
                    assertThat(n.getRecipientCustomerId()).isEqualTo(100L);
                    assertThat(n.getRecipientRole()).isEqualTo("ROLE_USER");
                    assertThat(n.isRead()).isFalse();
                    assertThat(n.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
                })
                .verifyComplete();
    }

    @Test
    void shouldDeleteById() {
        Notification saved = repository.save(
                buildNotification(1L, null, null, false, DeliveryStatus.DELIVERED, LocalDateTime.now())
        ).block();

        repository.deleteById(saved.getId()).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .verifyComplete();
    }

    @Test
    void findAllByRecipientUserIdOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildNotification(1L, null, null, false, DeliveryStatus.DELIVERED, now.minusHours(2))).block();
        repository.save(buildNotification(1L, null, null, false, DeliveryStatus.DELIVERED, now.minusHours(1))).block();
        repository.save(buildNotification(2L, null, null, false, DeliveryStatus.DELIVERED, now)).block();

        StepVerifier.create(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildNotification(1L, null, null, false, DeliveryStatus.DELIVERED, now.minusHours(1))).block();
        repository.save(buildNotification(1L, null, null, true, DeliveryStatus.DELIVERED, now)).block();

        StepVerifier.create(repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(1L))
                .assertNext(n -> assertThat(n.isRead()).isFalse())
                .verifyComplete();
    }

    @Test
    void findAllByRecipientRoleOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildNotification(null, null, "ROLE_ADMIN", false, DeliveryStatus.DELIVERED, now.minusHours(1))).block();
        repository.save(buildNotification(null, null, "ROLE_USER", false, DeliveryStatus.DELIVERED, now)).block();

        StepVerifier.create(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_ADMIN"))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc() {
        LocalDateTime now = LocalDateTime.now();
        repository.save(buildNotification(null, null, "ROLE_USER", false, DeliveryStatus.DELIVERED, now.minusHours(1))).block();
        repository.save(buildNotification(null, null, "ROLE_USER", true, DeliveryStatus.DELIVERED, now)).block();

        StepVerifier.create(repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc("ROLE_USER"))
                .assertNext(n -> assertThat(n.isRead()).isFalse())
                .verifyComplete();
    }
}
