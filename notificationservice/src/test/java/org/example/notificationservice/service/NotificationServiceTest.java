package org.example.notificationservice.service;

import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.exception.ForbiddenException;
import org.example.notificationservice.exception.NotFoundException;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.Notification;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.model.RelatedEntityType;
import org.example.notificationservice.repository.NotificationRepository;
import org.example.notificationservice.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

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

    private void signedIn(Long userId, String role) {
        securityUtilMock.when(SecurityUtil::userId).thenReturn(Mono.just(userId));
        securityUtilMock.when(SecurityUtil::role).thenReturn(Mono.just(role));
    }

    private void admin() {
        signedIn(1L, "ROLE_ADMIN");
        securityUtilMock.when(SecurityUtil::requireAdmin).thenReturn(Mono.empty());
    }

    private void notAdmin() {
        securityUtilMock.when(SecurityUtil::requireAdmin)
                .thenReturn(Mono.error(new ForbiddenException("Admin access required")));
    }

    private static Notification notification(Long id, Long userId, Long customerId, String role, boolean read) {
        return Notification.builder()
                .id(id)
                .notificationReference("NTF-TEST-" + id)
                .recipientUserId(userId)
                .recipientCustomerId(customerId)
                .recipientRole(role)
                .type(NotificationType.GENERAL)
                .title("Title " + id)
                .message("Message " + id)
                .read(read)
                .createdAt(LocalDateTime.now().minusMinutes(id))
                .build();
    }

    private static InternalNotificationRequest.InternalNotificationRequestBuilder request() {
        return InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.POLICY_SUBMITTED)
                .title("Policy Submitted")
                .message("Your policy has been submitted");
    }

    private void saveAssignsId() {
        when(repository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) n.setId(1L);
            return Mono.just(n);
        });
    }

    /** Stubs the three inbox queries for user 1 / ROLE_CUSTOMER. */
    private void inbox(boolean unreadOnly, Flux<Notification> byUser, Flux<Notification> byCustomer, Flux<Notification> byRole) {
        if (unreadOnly) {
            when(repository.findAllByRecipientUserIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(byUser);
            when(repository.findAllByRecipientCustomerIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(byCustomer);
            when(repository.findAllByRecipientRoleAndReadFalseOrderByCreatedAtDesc("ROLE_CUSTOMER")).thenReturn(byRole);
        } else {
            when(repository.findAllByRecipientUserIdOrderByCreatedAtDesc(1L)).thenReturn(byUser);
            when(repository.findAllByRecipientCustomerIdOrderByCreatedAtDesc(1L)).thenReturn(byCustomer);
            when(repository.findAllByRecipientRoleOrderByCreatedAtDesc("ROLE_CUSTOMER")).thenReturn(byRole);
        }
    }

    // ---- create ----

    @Test
    void create_validRequest_savesPublishesAndReturnsResponse() {
        saveAssignsId();

        StepVerifier.create(service.create(request().build()))
                .assertNext(r -> {
                    assertThat(r.getId()).isEqualTo(1L);
                    assertThat(r.getNotificationReference()).startsWith("NTF-").hasSize(20);
                    assertThat(r.getTitle()).isEqualTo("Policy Submitted");
                    assertThat(r.getType()).isEqualTo(NotificationType.POLICY_SUBMITTED);
                    assertThat(r.isRead()).isFalse();
                    assertThat(r.getCreatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(repository).save(any(Notification.class));
        verify(stream).publish(any(NotificationResponse.class));
    }

    @Test
    void create_noRecipientIdentifier_throwsValidationException() {
        StepVerifier.create(service.create(request().recipientUserId(null).build()))
                .expectErrorMatches(e -> e instanceof ValidationException
                        && e.getMessage().contains("recipient"))
                .verify();
        verifyNoInteractions(repository, stream);
    }

    @Test
    void create_blankRoleOnly_throwsValidationException() {
        StepVerifier.create(service.create(request().recipientUserId(null).recipientRole("  ").build()))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void create_relatedEntityIdWithoutEntityType_throwsValidationException() {
        StepVerifier.create(service.create(request().relatedEntityId(5L).build()))
                .expectErrorMatches(e -> e instanceof ValidationException
                        && e.getMessage().contains("relatedEntityType"))
                .verify();
    }

    @Test
    void create_blankTitle_throwsValidationException() {
        StepVerifier.create(service.create(request().title("   ").build()))
                .expectErrorMatches(e -> e.getMessage().equals("title must not be blank"))
                .verify();
    }

    @Test
    void create_nullMessage_throwsValidationException() {
        StepVerifier.create(service.create(request().message(null).build()))
                .expectErrorMatches(e -> e.getMessage().equals("message must not be blank"))
                .verify();
    }

    @Test
    void create_titleAndMessageTrimmed() {
        saveAssignsId();

        StepVerifier.create(service.create(request().title("  Hello  ").message("  World  ").build()))
                .assertNext(r -> {
                    assertThat(r.getTitle()).isEqualTo("Hello");
                    assertThat(r.getMessage()).isEqualTo("World");
                })
                .verifyComplete();
    }

    @Test
    void create_roleNormalizedWithPrefix() {
        saveAssignsId();

        StepVerifier.create(service.create(request().recipientUserId(null).recipientRole("UNDERWRITER").build()))
                .assertNext(r -> assertThat(r.getRecipientRole()).isEqualTo("ROLE_UNDERWRITER"))
                .verifyComplete();
    }

    @Test
    void create_roleAlreadyPrefixed_unchanged() {
        saveAssignsId();

        StepVerifier.create(service.create(request().recipientRole("ROLE_ADMIN").build()))
                .assertNext(r -> assertThat(r.getRecipientRole()).isEqualTo("ROLE_ADMIN"))
                .verifyComplete();
    }

    @Test
    void create_blankRoleWithUser_storesNullRole() {
        saveAssignsId();

        StepVerifier.create(service.create(request().recipientRole(" ").build()))
                .assertNext(r -> assertThat(r.getRecipientRole()).isNull())
                .verifyComplete();
    }

    @Test
    void create_customerOnlyRecipient_isAccepted() {
        saveAssignsId();

        StepVerifier.create(service.create(request().recipientUserId(null).recipientCustomerId(7L).build()))
                .assertNext(r -> assertThat(r.getRecipientCustomerId()).isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void create_relatedEntityAndCorrelationStored() {
        saveAssignsId();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        StepVerifier.create(service.create(request()
                        .relatedEntityType(RelatedEntityType.CLAIM).relatedEntityId(9L)
                        .correlationId("corr-1").build()))
                .assertNext(r -> {
                    assertThat(r.getRelatedEntityType()).isEqualTo(RelatedEntityType.CLAIM);
                    assertThat(r.getRelatedEntityId()).isEqualTo(9L);
                })
                .verifyComplete();

        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCorrelationId()).isEqualTo("corr-1");
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void create_newIdempotencyKey_savesTrimmedKey() {
        when(repository.findFirstByIdempotencyKey("key-1")).thenReturn(Mono.empty());
        saveAssignsId();
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);

        StepVerifier.create(service.create(request().idempotencyKey("  key-1 ").build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("key-1");
        verify(stream).publish(any(NotificationResponse.class));
    }

    @Test
    void create_duplicateIdempotencyKey_returnsExistingWithoutSavingOrPublishing() {
        when(repository.findFirstByIdempotencyKey("key-1"))
                .thenReturn(Mono.just(notification(42L, 1L, null, null, false)));

        StepVerifier.create(service.create(request().idempotencyKey("key-1").build()))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(42L))
                .verifyComplete();

        verify(repository, never()).save(any());
        verifyNoInteractions(stream);
    }

    @Test
    void create_blankIdempotencyKey_skipsLookup() {
        saveAssignsId();

        StepVerifier.create(service.create(request().idempotencyKey("   ").build()))
                .expectNextCount(1)
                .verifyComplete();

        verify(repository, never()).findFirstByIdempotencyKey(any());
    }

    @Test
    void create_concurrentDuplicateKey_returnsRowSavedByOtherRequest() {
        when(repository.findFirstByIdempotencyKey("key-1"))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(notification(43L, 1L, null, null, false)));
        when(repository.save(any(Notification.class))).thenReturn(Mono.error(new DuplicateKeyException("dup")));

        StepVerifier.create(service.create(request().idempotencyKey("key-1").build()))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(43L))
                .verifyComplete();

        verifyNoInteractions(stream);
    }

    // ---- inbox ----

    @Test
    void mine_returnsUserCustomerAndRoleNotificationsNewestFirst() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(false,
                Flux.just(notification(3L, 1L, null, null, false)),
                Flux.just(notification(1L, null, 1L, null, true)),
                Flux.just(notification(2L, null, null, "ROLE_CUSTOMER", false)));

        StepVerifier.create(service.mine(false))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(1L))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(2L))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    void mine_deduplicatesNotificationsMatchingSeveralWays() {
        signedIn(1L, "ROLE_CUSTOMER");
        Notification both = notification(5L, 1L, 1L, "ROLE_CUSTOMER", false);
        inbox(false, Flux.just(both), Flux.just(both), Flux.just(both));

        StepVerifier.create(service.mine(false))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void mine_roleNotificationForSomeoneElse_isHidden() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(false, Flux.empty(), Flux.empty(),
                Flux.just(notification(6L, 99L, null, "ROLE_CUSTOMER", false),
                        notification(7L, null, 98L, "ROLE_CUSTOMER", false)));

        StepVerifier.create(service.mine(false)).verifyComplete();
    }

    @Test
    void mine_unreadOnly_usesUnreadQueries() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(true, Flux.just(notification(1L, 1L, null, null, false)), Flux.empty(), Flux.empty());

        StepVerifier.create(service.mine(true))
                .assertNext(r -> assertThat(r.isRead()).isFalse())
                .verifyComplete();

        verify(repository, never()).findAllByRecipientUserIdOrderByCreatedAtDesc(any());
    }

    @Test
    void unreadCount_countsUnreadInbox() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(true,
                Flux.just(notification(1L, 1L, null, null, false), notification(2L, 1L, null, null, false)),
                Flux.empty(),
                Flux.just(notification(3L, null, null, "ROLE_CUSTOMER", false)));

        StepVerifier.create(service.unreadCount())
                .assertNext(r -> assertThat(r.getUnreadCount()).isEqualTo(3L))
                .verifyComplete();
    }

    @Test
    void unreadCount_emptyInbox_isZero() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(true, Flux.empty(), Flux.empty(), Flux.empty());

        StepVerifier.create(service.unreadCount())
                .assertNext(r -> assertThat(r.getUnreadCount()).isZero())
                .verifyComplete();
    }

    // ---- live stream ----

    @Test
    void stream_deliversOnlyNotificationsForCurrentUser() {
        signedIn(1L, "ROLE_CUSTOMER");
        Sinks.Many<NotificationResponse> sink = Sinks.many().multicast().directBestEffort();
        when(stream.asFlux()).thenReturn(sink.asFlux());

        StepVerifier.create(service.stream())
                .then(() -> {
                    sink.tryEmitNext(NotificationResponse.builder().id(1L).recipientUserId(1L).build());
                    sink.tryEmitNext(NotificationResponse.builder().id(2L).recipientUserId(2L).build());
                    sink.tryEmitNext(NotificationResponse.builder().id(3L).recipientCustomerId(1L).build());
                    sink.tryEmitNext(NotificationResponse.builder().id(4L).recipientRole("ROLE_CUSTOMER").build());
                    sink.tryEmitNext(NotificationResponse.builder().id(5L).recipientRole("ROLE_ADMIN").build());
                    sink.tryEmitNext(NotificationResponse.builder().id(6L).recipientUserId(9L).recipientRole("ROLE_CUSTOMER").build());
                    sink.tryEmitComplete();
                })
                .assertNext(r -> assertThat(r.getId()).isEqualTo(1L))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(3L))
                .assertNext(r -> assertThat(r.getId()).isEqualTo(4L))
                .verifyComplete();
    }

    // ---- mark read ----

    @Test
    void markRead_unreadNotification_marksAsRead() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, 1L, null, null, false)));
        saveAssignsId();

        StepVerifier.create(service.markRead(1L))
                .assertNext(r -> {
                    assertThat(r.isRead()).isTrue();
                    assertThat(r.getReadAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void markRead_alreadyRead_doesNotSaveAgain() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, 1L, null, null, true)));

        StepVerifier.create(service.markRead(1L))
                .assertNext(r -> assertThat(r.isRead()).isTrue())
                .verifyComplete();

        verify(repository, never()).save(any());
    }

    @Test
    void markRead_roleBroadcast_allowedForThatRole() {
        signedIn(1L, "ROLE_UNDERWRITER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, null, null, "ROLE_UNDERWRITER", false)));
        saveAssignsId();

        StepVerifier.create(service.markRead(1L))
                .assertNext(r -> assertThat(r.isRead()).isTrue())
                .verifyComplete();
    }

    @Test
    void markRead_customerNotification_allowedForThatUser() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, null, 1L, null, false)));
        saveAssignsId();

        StepVerifier.create(service.markRead(1L))
                .assertNext(r -> assertThat(r.isRead()).isTrue())
                .verifyComplete();
    }

    @Test
    void markRead_notFound_throwsNotFoundException() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.markRead(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void markRead_notOwned_throwsForbiddenException() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, 2L, null, null, false)));

        StepVerifier.create(service.markRead(1L))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void markRead_roleBroadcastForAnotherRole_throwsForbiddenException() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, null, null, "ROLE_ADMIN", false)));

        StepVerifier.create(service.markRead(1L))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void markAllRead_marksEveryUnreadInboxItem() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(true,
                Flux.just(notification(1L, 1L, null, null, false)),
                Flux.just(notification(2L, null, 1L, null, false)),
                Flux.just(notification(3L, null, null, "ROLE_CUSTOMER", false)));
        saveAssignsId();

        StepVerifier.create(service.markAllRead())
                .assertNext(r -> assertThat(r.getMessage()).isEqualTo("3 notification(s) marked as read"))
                .verifyComplete();

        verify(repository, times(3)).save(any(Notification.class));
    }

    @Test
    void markAllRead_nothingUnread_reportsZero() {
        signedIn(1L, "ROLE_CUSTOMER");
        inbox(true, Flux.empty(), Flux.empty(), Flux.empty());

        StepVerifier.create(service.markAllRead())
                .assertNext(r -> assertThat(r.getMessage()).startsWith("0 "))
                .verifyComplete();
    }

    // ---- delete ----

    @Test
    void delete_ownNotification_deletes() {
        signedIn(1L, "ROLE_CUSTOMER");
        Notification n = notification(1L, 1L, null, null, false);
        when(repository.findById(1L)).thenReturn(Mono.just(n));
        when(repository.delete(n)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete(1L))
                .assertNext(r -> assertThat(r.getMessage()).isEqualTo("Notification deleted"))
                .verifyComplete();

        verify(repository).delete(n);
    }

    @Test
    void delete_notFound_throwsNotFoundException() {
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void delete_notOwned_throwsForbiddenException() {
        signedIn(1L, "ROLE_CUSTOMER");
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, 2L, null, null, false)));

        StepVerifier.create(service.delete(1L))
                .expectError(ForbiddenException.class)
                .verify();

        verify(repository, never()).delete(any(Notification.class));
    }

    // ---- admin ----

    @Test
    void all_admin_returnsEveryNotification() {
        admin();
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(Flux.just(notification(1L, 1L, null, null, false), notification(2L, 2L, null, null, true)));

        StepVerifier.create(service.all())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void all_nonAdmin_throwsForbiddenException() {
        notAdmin();

        StepVerifier.create(service.all())
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void adminMarkRead_marksAnyNotification() {
        admin();
        when(repository.findById(1L)).thenReturn(Mono.just(notification(1L, 99L, null, null, false)));
        saveAssignsId();

        StepVerifier.create(service.adminMarkRead(1L))
                .assertNext(r -> assertThat(r.isRead()).isTrue())
                .verifyComplete();
    }

    @Test
    void adminMarkRead_notFound_throwsNotFoundException() {
        admin();
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.adminMarkRead(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void adminMarkRead_nonAdmin_throwsForbiddenException() {
        notAdmin();

        StepVerifier.create(service.adminMarkRead(1L))
                .expectError(ForbiddenException.class)
                .verify();

        verify(repository, never()).findById(any(Long.class));
    }

    @Test
    void adminDelete_deletesAnyNotification() {
        admin();
        Notification n = notification(1L, 99L, null, null, false);
        when(repository.findById(1L)).thenReturn(Mono.just(n));
        when(repository.delete(n)).thenReturn(Mono.empty());

        StepVerifier.create(service.adminDelete(1L))
                .assertNext(r -> assertThat(r.getMessage()).isEqualTo("Notification deleted"))
                .verifyComplete();
    }

    @Test
    void adminDelete_notFound_throwsNotFoundException() {
        admin();
        when(repository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.adminDelete(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void adminDelete_nonAdmin_throwsForbiddenException() {
        notAdmin();

        StepVerifier.create(service.adminDelete(1L))
                .expectError(ForbiddenException.class)
                .verify();
    }
}
