package org.example.notificationservice.service;

import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.MessageResponse;
import org.example.notificationservice.dto.NotificationQuery;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.SendFromTemplateRequest;
import org.example.notificationservice.dto.UnreadCountResponse;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.DeliveryAttempt;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationTemplate;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationTemplateService templateService;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private DeliveryAttemptService deliveryAttemptService;

    @Mock
    private NotificationDeliveryService deliveryService;

    @Mock
    private NotificationStream stream;

    @InjectMocks
    private NotificationService service;

    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        securityUtilMock = mockStatic(SecurityUtil.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
    }

    private void setupSecurityContext(Long userId, Long customerId, String role) {
        securityUtilMock.when(SecurityUtil::userId).thenReturn(Mono.just(userId));
        securityUtilMock.when(SecurityUtil::customerId).thenReturn(customerId != null ? Mono.just(customerId) : Mono.empty());
        securityUtilMock.when(SecurityUtil::role).thenReturn(Mono.just(role));
    }

    private void setupAdminContext() {
        setupSecurityContext(1L, 100L, "ROLE_ADMIN");
        securityUtilMock.when(SecurityUtil::requireAdmin).thenReturn(Mono.empty());
    }

    private void setupNonAdminContext() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        securityUtilMock.when(SecurityUtil::requireAdmin)
                .thenReturn(Mono.error(new ForbiddenException("Admin access required")));
    }

    private Notification buildNotification(Long id, Long userId, Long customerId, String role,
                                           boolean read, DeliveryStatus status) {
        return Notification.builder()
                .id(id)
                .notificationReference("NTF-TEST-" + id)
                .recipientUserId(userId)
                .recipientCustomerId(customerId)
                .recipientRole(role)
                .type(NotificationType.GENERAL)
                .title("Test Title")
                .message("Test Message")
                .read(read)
                .deliveryChannel(DeliveryChannel.IN_APP)
                .deliveryStatus(status)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void create_validRequest_savesAndReturnsResponse() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.POLICY_SUBMITTED)
                .title("Policy Submitted")
                .message("Your policy has been submitted")
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getNotificationReference()).startsWith("NTF-");
                    assertThat(response.getTitle()).isEqualTo("Policy Submitted");
                    assertThat(response.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
                    assertThat(response.isRead()).isFalse();
                })
                .verifyComplete();

        verify(repository, times(2)).save(any(Notification.class));
        verify(deliveryAttemptService).recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any());
    }

    @Test
    void create_noRecipientIdentifier_throwsValidationException() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .build();

        StepVerifier.create(service.create(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void create_relatedEntityIdWithoutEntityType_throwsValidationException() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .relatedEntityId(100L)
                .build();

        StepVerifier.create(service.create(request))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void create_blankRoleIgnoredWhenOtherIdentifiersPresent() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .recipientRole("  ")
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void create_roleNormalized() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .recipientRole("ADMIN")
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> assertThat(response.getRecipientRole()).isEqualTo("ROLE_ADMIN"))
                .verifyComplete();
    }

    @Test
    void create_roleAlreadyNormalized_noChange() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .recipientRole("ROLE_USER")
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> assertThat(response.getRecipientRole()).isEqualTo("ROLE_USER"))
                .verifyComplete();
    }

    @Test
    void mine_unreadOnlyFalse_returnsAllNotifications() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n1 = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        Notification n2 = buildNotification(2L, 1L, 100L, "ROLE_USER", true, DeliveryStatus.DELIVERED);

        when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1, n2));
        when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(false))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void mine_unreadOnlyTrue_returnsOnlyUnread() {
        setupSecurityContext(1L, -1L, "ROLE_USER");
        Notification n1 = buildNotification(1L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1));
        when(repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(true))
                .assertNext(response -> assertThat(response.isRead()).isFalse())
                .verifyComplete();
    }

    @Test
    void mine_mergesAndDeduplicatesNotifications() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n1 = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        Notification n2 = buildNotification(2L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1));
        when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n2));
        when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(false))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void unreadCount_returnsCorrectCount() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n1 = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        Notification n2 = buildNotification(2L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1, n2));
        when(repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.unreadCount())
                .assertNext(count -> assertThat(count.getUnreadCount()).isEqualTo(2))
                .verifyComplete();
    }

    @Test
    void markRead_unreadNotification_marksAsRead() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findById(1L)).thenReturn(Mono.just(n));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.markRead(1L))
                .assertNext(response -> {
                    assertThat(response.isRead()).isTrue();
                    assertThat(response.getReadAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void markRead_alreadyRead_doesNotChangeReadAt() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        LocalDateTime readAt = LocalDateTime.now().minusHours(1);
        Notification n = buildNotification(1L, 1L, 100L, "ROLE_USER", true, DeliveryStatus.DELIVERED);
        n.setReadAt(readAt);

        when(repository.findById(1L)).thenReturn(Mono.just(n));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.markRead(1L))
                .assertNext(response -> assertThat(response.getReadAt()).isEqualTo(readAt))
                .verifyComplete();
    }

    @Test
    void markRead_notFound_throwsNotFoundException() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        when(repository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.markRead(99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void markRead_notOwned_throwsForbiddenException() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n = buildNotification(1L, 2L, 200L, "ROLE_OTHER", false, DeliveryStatus.DELIVERED);

        when(repository.findById(1L)).thenReturn(Mono.just(n));

        StepVerifier.create(service.markRead(1L))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void markAllRead_marksAllUnreadAsRead() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n1 = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        Notification n2 = buildNotification(2L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1, n2));
        when(repository.save(any(Notification.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.markAllRead())
                .assertNext(response -> assertThat(response.getMessage()).contains("2 notification(s) marked as read"))
                .verifyComplete();
    }

    @Test
    void delete_existingNotification_deletesSuccessfully() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);

        when(repository.findById(1L)).thenReturn(Mono.just(n));
        when(repository.delete(n)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete(1L))
                .assertNext(response -> assertThat(response.getMessage()).isEqualTo("Notification deleted"))
                .verifyComplete();
    }

    @Test
    void delete_notFound_throwsNotFoundException() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        when(repository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete(99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void delete_notOwned_throwsForbiddenException() {
        setupSecurityContext(1L, 100L, "ROLE_USER");
        Notification n = buildNotification(1L, 2L, 200L, "ROLE_OTHER", false, DeliveryStatus.DELIVERED);

        when(repository.findById(1L)).thenReturn(Mono.just(n));

        StepVerifier.create(service.delete(1L))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void all_adminUser_returnsAllNotifications() {
        setupAdminContext();
        Notification n1 = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        Notification n2 = buildNotification(2L, 2L, 200L, "ROLE_ADMIN", false, DeliveryStatus.FAILED);

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(Flux.just(n1, n2));

        StepVerifier.create(service.all())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void all_nonAdmin_throwsForbiddenException() {
        setupNonAdminContext();

        StepVerifier.create(service.all())
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void failed_adminUser_returnsFailedNotifications() {
        setupAdminContext();
        Notification n = buildNotification(1L, 1L, 100L, "ROLE_USER", false, DeliveryStatus.FAILED);

        when(repository.findAllByDeliveryStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED))
                .thenReturn(Flux.just(n));

        StepVerifier.create(service.failed())
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void failed_nonAdmin_throwsForbiddenException() {
        setupNonAdminContext();

        StepVerifier.create(service.failed())
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void create_defaultDeliveryChannel_whenNull() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.GENERAL)
                .title("Test")
                .message("Test message")
                .deliveryChannel(null)
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> assertThat(response.getDeliveryChannel()).isEqualTo(DeliveryChannel.IN_APP))
                .verifyComplete();
    }

    @Test
    void create_titleAndMessageTrimmed() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.GENERAL)
                .title("  Trimmed Title  ")
                .message("  Trimmed Message  ")
                .build();

        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> {
                    assertThat(response.getTitle()).isEqualTo("Trimmed Title");
                    assertThat(response.getMessage()).isEqualTo("Trimmed Message");
                })
                .verifyComplete();
    }

    @Test
    void create_duplicateIdempotencyKey_returnsExistingWithoutCreatingSecondRow() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.TRIGGER_EVENT_VERIFIED)
                .title("Trigger Verified")
                .message("Your trigger was verified")
                .idempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25")
                .build();

        Notification existing = buildNotification(99L, 1L, 25L, "ROLE_CUSTOMER", false, DeliveryStatus.DELIVERED);
        existing.setIdempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25");

        when(repository.findFirstByIdempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.create(request))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(99L))
                .verifyComplete();

        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void create_withIdempotencyKey_firstSubmission_savesAndMarksDelivered() {
        InternalNotificationRequest request = InternalNotificationRequest.builder()
                .recipientCustomerId(25L)
                .type(NotificationType.TRIGGER_EVENT_VERIFIED)
                .title("Trigger Verified")
                .message("Your trigger was verified")
                .idempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25")
                .build();

        when(repository.findFirstByIdempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25")).thenReturn(Mono.empty());
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) n.setId(7L);
            return Mono.just(n);
        });
        when(deliveryAttemptService.recordAttempt(anyLong(), any(DeliveryChannel.class), any(DeliveryStatus.class), any()))
                .thenReturn(Mono.just(DeliveryAttempt.builder().id(1L).build()));

        StepVerifier.create(service.create(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(7L);
                    assertThat(response.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
                    assertThat(response.getIdempotencyKey()).isEqualTo("TRIGGER_VERIFIED:41:CUSTOMER:25");
                    assertThat(response.isRead()).isFalse();
                })
                .verifyComplete();

        verify(repository).findFirstByIdempotencyKey("TRIGGER_VERIFIED:41:CUSTOMER:25");
    }

    @Test
    void mine_createdAfterFilter_returnsOnlyNewerNotifications() {
        setupSecurityContext(1L, -1L, "ROLE_USER");
        LocalDateTime now = LocalDateTime.now();
        Notification older = buildNotification(1L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        older.setCreatedAt(now.minusHours(2));
        Notification newer = buildNotification(2L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        newer.setCreatedAt(now);

        when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(newer, older));
        when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(NotificationQuery.of(false, null, now.minusHours(1), 0, 20, false, true)))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(2L))
                .verifyComplete();
    }

    @Test
    void mine_sortAscending_returnsOldestFirst() {
        setupSecurityContext(1L, -1L, "ROLE_USER");
        LocalDateTime now = LocalDateTime.now();
        Notification older = buildNotification(1L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        older.setCreatedAt(now.minusHours(2));
        Notification newer = buildNotification(2L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        newer.setCreatedAt(now);

        when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(newer, older));
        when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(NotificationQuery.of(false, null, null, 0, 100, true, true)))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(1L))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(2L))
                .verifyComplete();
    }

    @Test
    void mine_paginatesResults() {
        setupSecurityContext(1L, -1L, "ROLE_USER");
        LocalDateTime now = LocalDateTime.now();
        Notification n1 = buildNotification(1L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        n1.setCreatedAt(now);
        Notification n2 = buildNotification(2L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        n2.setCreatedAt(now.minusHours(1));
        Notification n3 = buildNotification(3L, 1L, null, "ROLE_USER", false, DeliveryStatus.DELIVERED);
        n3.setCreatedAt(now.minusHours(2));

        when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.just(n1, n2, n3));
        when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(Flux.empty());
        when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_USER")).thenReturn(Flux.empty());

        StepVerifier.create(service.mine(NotificationQuery.of(false, null, null, 0, 2, false, true)))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(1L))
                .assertNext(response -> assertThat(response.getId()).isEqualTo(2L))
                .verifyComplete();
    }

    @Test
    void sendFromTemplate_missingVariablesRenderedSafely() {
        SendFromTemplateRequest request = SendFromTemplateRequest.builder()
                .notificationType(NotificationType.TRIGGER_EVENT_VERIFIED)
                .recipientCustomerId(25L)
                .variables(Map.of("perilName", "Rainfall", "zoneName", "Zone A"))
                .build();

        NotificationTemplate template = NotificationTemplate.builder()
                .notificationType(NotificationType.TRIGGER_EVENT_VERIFIED)
                .titleTemplate("Your parametric coverage has been triggered")
                .messageTemplate("A verified {perilName} event was recorded in {zoneName}. The measured value was {observedValue} {unit}, meeting the policy threshold of {thresholdValue} {unit}.")
                .defaultChannel(DeliveryChannel.IN_APP)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(templateService.getDefaultTemplate(NotificationType.TRIGGER_EVENT_VERIFIED)).thenReturn(Mono.just(template));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryService.dispatch(any(Notification.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.sendFromTemplate(request))
                .assertNext(response -> {
                    assertThat(response.getType()).isEqualTo(NotificationType.TRIGGER_EVENT_VERIFIED);
                    assertThat(response.getTitle()).isEqualTo("Your parametric coverage has been triggered");
                    assertThat(response.getMessage()).contains("A verified Rainfall event was recorded in Zone A.");
                    assertThat(response.getMessage()).doesNotContain("{observedValue}");
                    assertThat(response.getMessage()).doesNotContain("{thresholdValue}");
                    assertThat(response.getMessage()).doesNotContain("null");
                    assertThat(response.getMessage()).doesNotContain("undefined");
                    assertThat(response.getRecipientCustomerId()).isEqualTo(25L);
                })
                .verifyComplete();
    }

    @Test
    void batch_withIdempotencyKey_createsSingleRow() {
        InternalNotificationRequest one = InternalNotificationRequest.builder()
                .recipientCustomerId(25L)
                .type(NotificationType.PAYOUT_SUCCESS)
                .title("Payout completed")
                .message("The payout was completed successfully.")
                .idempotencyKey("PAYOUT_SUCCESS:1:CUSTOMER:25")
                .build();

        when(repository.findFirstByIdempotencyKey("PAYOUT_SUCCESS:1:CUSTOMER:25")).thenReturn(Mono.empty());
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            if (n.getId() == null) n.setId(1L);
            return Mono.just(n);
        });
        when(deliveryService.dispatch(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setDeliveryStatus(DeliveryStatus.DELIVERED);
            return Mono.just(n);
        });

        StepVerifier.create(service.batch(List.of(one)))
                .assertNext(response -> {
                    assertThat(response.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
                    assertThat(response.getTitle()).isEqualTo("Payout completed");
                })
                .verifyComplete();

        verify(repository).findFirstByIdempotencyKey("PAYOUT_SUCCESS:1:CUSTOMER:25");
    }
}
