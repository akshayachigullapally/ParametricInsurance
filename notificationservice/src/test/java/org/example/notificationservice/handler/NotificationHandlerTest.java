package org.example.notificationservice.handler;

import org.example.notificationservice.dto.InternalNotificationRequest;
import org.example.notificationservice.dto.MessageResponse;
import org.example.notificationservice.dto.NotificationQuery;
import org.example.notificationservice.dto.NotificationResponse;
import org.example.notificationservice.dto.UnreadCountResponse;
import org.example.notificationservice.exception.ValidationException;
import org.example.notificationservice.model.DeliveryChannel;
import org.example.notificationservice.model.DeliveryStatus;
import org.example.notificationservice.model.NotificationType;
import org.example.notificationservice.model.RelatedEntityType;
import org.example.notificationservice.service.NotificationService;
import org.example.notificationservice.util.RequestValidationUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private NotificationService service;

    @Mock
    private RequestValidationUtil validation;

    @InjectMocks
    private NotificationHandler handler;

    private NotificationResponse buildResponse(Long id) {
        return NotificationResponse.builder()
                .id(id)
                .notificationReference("NTF-TEST-" + id)
                .recipientUserId(1L)
                .type(NotificationType.GENERAL)
                .title("Test Title")
                .message("Test Message")
                .deliveryChannel(DeliveryChannel.IN_APP)
                .deliveryStatus(DeliveryStatus.DELIVERED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ServerRequest mockGetRequest(String path) {
        return mock(ServerRequest.class);
    }

    private ServerRequest mockRequestWithPathVariable(String varName, String value) {
        ServerRequest request = mock(ServerRequest.class);
        when(request.pathVariable(varName)).thenReturn(value);
        return request;
    }

    @Test
    void mine_returnsNotifications() {
        ServerRequest request = mockGetRequest("/api/notifications/me");
        when(service.mine(any(NotificationQuery.class))).thenReturn(Flux.just(buildResponse(1L), buildResponse(2L)));

        StepVerifier.create(handler.mine(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void unread_returnsUnreadNotifications() {
        ServerRequest request = mockGetRequest("/api/notifications/me/unread");
        when(service.mine(true)).thenReturn(Flux.just(buildResponse(1L)));

        StepVerifier.create(handler.unread(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void count_returnsUnreadCount() {
        ServerRequest request = mockGetRequest("/api/notifications/me/unread-count");
        when(service.unreadCount()).thenReturn(Mono.just(new UnreadCountResponse(5)));

        StepVerifier.create(handler.count(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void read_validId_marksAsRead() {
        ServerRequest request = mockRequestWithPathVariable("notificationId", "1");
        when(service.markRead(1L)).thenReturn(Mono.just(buildResponse(1L)));

        StepVerifier.create(handler.read(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void read_invalidId_throwsValidationException() {
        ServerRequest request = mockRequestWithPathVariable("notificationId", "abc");

        assertThatThrownBy(() -> handler.read(request).block())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid notificationId");
    }

    @Test
    void readAll_marksAllAsRead() {
        ServerRequest request = mockGetRequest("/api/notifications/me/read-all");
        when(service.markAllRead()).thenReturn(Mono.just(new MessageResponse("3 notification(s) marked as read")));

        StepVerifier.create(handler.readAll(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void delete_validId_deletesNotification() {
        ServerRequest request = mockRequestWithPathVariable("notificationId", "1");
        when(service.delete(1L)).thenReturn(Mono.just(new MessageResponse("Notification deleted")));

        StepVerifier.create(handler.delete(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void delete_invalidId_throwsValidationException() {
        ServerRequest request = mockRequestWithPathVariable("notificationId", "abc");

        assertThatThrownBy(() -> handler.delete(request).block())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid notificationId");
    }

    @Test
    void all_returnsAllNotifications() {
        ServerRequest request = mockGetRequest("/api/notifications/admin/all");
        when(service.all()).thenReturn(Flux.just(buildResponse(1L), buildResponse(2L)));

        StepVerifier.create(handler.all(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void failed_returnsFailedNotifications() {
        ServerRequest request = mockGetRequest("/api/notifications/admin/failed");
        when(service.failed()).thenReturn(Flux.just(buildResponse(1L)));

        StepVerifier.create(handler.failed(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void create_validRequest_returnsCreated() {
        ServerRequest request = mock(ServerRequest.class);
        InternalNotificationRequest body = InternalNotificationRequest.builder()
                .recipientUserId(1L)
                .type(NotificationType.POLICY_SUBMITTED)
                .title("Policy Submitted")
                .message("Your policy has been submitted")
                .relatedEntityType(RelatedEntityType.POLICY)
                .relatedEntityId(100L)
                .build();

        when(request.bodyToMono(InternalNotificationRequest.class)).thenReturn(Mono.just(body));
        when(validation.validate(any(InternalNotificationRequest.class))).thenReturn(body);
        when(service.create(any(InternalNotificationRequest.class))).thenReturn(Mono.just(buildResponse(1L)));

        StepVerifier.create(handler.create(request))
                .assertNext(response -> {
                    assertThat(response.statusCode()).isEqualTo(HttpStatus.CREATED);
                })
                .verifyComplete();
    }

    @Test
    void create_invalidBody_throwsValidationException() {
        ServerRequest request = mock(ServerRequest.class);
        InternalNotificationRequest body = InternalNotificationRequest.builder()
                .type(NotificationType.GENERAL)
                .title("")
                .message("Test")
                .build();

        when(request.bodyToMono(InternalNotificationRequest.class)).thenReturn(Mono.just(body));
        when(validation.validate(any(InternalNotificationRequest.class)))
                .thenThrow(new ValidationException("Title must not be blank"));

        assertThatThrownBy(() -> handler.create(request).block())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Title must not be blank");
    }
}
