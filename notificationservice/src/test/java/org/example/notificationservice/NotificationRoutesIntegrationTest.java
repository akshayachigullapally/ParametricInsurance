package org.example.notificationservice;

import org.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@AutoConfigureWebTestClient
class NotificationRoutesIntegrationTest {

    private static final String INTERNAL_KEY_HEADER = "X-Gateway-Key";
    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLE_HEADER = "X-Authenticated-Role";
    private static final String CUSTOMER_ID_HEADER = "X-Authenticated-Customer-Id";
    private static final String INTERNAL_KEY = "test-gateway-key";

    @Autowired
    private WebTestClient client;

    @Autowired
    private NotificationRepository repository;

    @BeforeEach
    void cleanUp() {
        repository.deleteAll().block();
    }

    private Map<String, Object> requestBody(Long customerId, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientUserId", 15L);
        body.put("recipientCustomerId", customerId);
        body.put("recipientRole", "CUSTOMER");
        body.put("type", "TRIGGER_EVENT_VERIFIED");
        body.put("title", "Your parametric coverage has been triggered");
        body.put("message", "A verified rainfall event was recorded for your policy zone.");
        body.put("relatedEntityType", "TRIGGER_EVENT");
        body.put("relatedEntityId", 41L);
        body.put("correlationId", "TEV-2026-00041");
        body.put("idempotencyKey", idempotencyKey);
        body.put("deliveryChannel", "IN_APP");
        return body;
    }

    @Test
    void internalCreate_missingGatewayKey_returns401() {
        client.post().uri("/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void internalCreate_invalidGatewayKey_returns401() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void internalCreate_validKey_createsDeliveredNotification() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNotEmpty()
                .jsonPath("$.type").isEqualTo("TRIGGER_EVENT_VERIFIED")
                .jsonPath("$.deliveryChannel").isEqualTo("IN_APP")
                .jsonPath("$.deliveryStatus").isEqualTo("DELIVERED")
                .jsonPath("$.read").isEqualTo(false)
                .jsonPath("$.correlationId").isEqualTo("TEV-2026-00041");
    }

    @Test
    void internalCreate_duplicateIdempotencyKey_createsSingleRow() {
        Map<String, Object> body = requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25");
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();

        assertThat(repository.count().block()).isEqualTo(1L);
    }

    @Test
    void customerRetrieval_returnsOnlyOwnNotifications() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isCreated();

        client.get().uri("/api/notifications/me")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .header(USER_ID_HEADER, "15")
                .header(ROLE_HEADER, "ROLE_CUSTOMER")
                .header(CUSTOMER_ID_HEADER, "25")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].recipientCustomerId").isEqualTo(25)
                .jsonPath("$[0].read").isEqualTo(false);
    }

    @Test
    void customerRetrieval_otherCustomerSeesNothing() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isCreated();

        client.get().uri("/api/notifications/me")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .header(USER_ID_HEADER, "16")
                .header(ROLE_HEADER, "ROLE_CUSTOMER")
                .header(CUSTOMER_ID_HEADER, "99")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(0);
    }

    @Test
    void unreadCount_returnsCount() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isCreated();

        client.get().uri("/api/notifications/me/unread-count")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .header(USER_ID_HEADER, "15")
                .header(ROLE_HEADER, "ROLE_CUSTOMER")
                .header(CUSTOMER_ID_HEADER, "25")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.count").isEqualTo(1);
    }

    @Test
    void notificationCreatedWhileOffline_remainsAvailableAfterLogin() {
        client.post().uri("/internal/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody(25L, "TRIGGER_VERIFIED:41:CUSTOMER:25"))
                .exchange()
                .expectStatus().isCreated();

        client.get().uri("/api/notifications/me")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .header(USER_ID_HEADER, "15")
                .header(ROLE_HEADER, "ROLE_CUSTOMER")
                .header(CUSTOMER_ID_HEADER, "25")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void noWebSocketRouteExists() {
        client.get().uri("/ws/notifications")
                .header(INTERNAL_KEY_HEADER, INTERNAL_KEY)
                .header(USER_ID_HEADER, "15")
                .header(ROLE_HEADER, "ROLE_CUSTOMER")
                .header(CUSTOMER_ID_HEADER, "25")
                .exchange()
                .expectStatus().isNotFound();
    }
}