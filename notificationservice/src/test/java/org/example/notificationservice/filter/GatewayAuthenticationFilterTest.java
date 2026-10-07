package org.example.notificationservice.filter;

import org.example.notificationservice.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayAuthenticationFilterTest {

    private static final String TEST_KEY = "test-gateway-key";
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(TEST_KEY);
    private final WebFilterChain chain = mockWebFilterChain();

    private WebFilterChain mockWebFilterChain() {
        WebFilterChain c = org.mockito.Mockito.mock(WebFilterChain.class);
        when(c.filter(any())).thenReturn(Mono.empty());
        return c;
    }

    private MockServerWebExchange createExchange(String path, String gatewayKey, String userId, String role, String customerId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        if (gatewayKey != null) {
            builder.header(GatewayHeaders.GATEWAY_KEY, gatewayKey);
        }
        if (userId != null) {
            builder.header(GatewayHeaders.USER_ID, userId);
        }
        if (role != null) {
            builder.header(GatewayHeaders.ROLE, role);
        }
        if (customerId != null) {
            builder.header(GatewayHeaders.CUSTOMER_ID, customerId);
        }
        return MockServerWebExchange.from(builder);
    }

    @Test
    void filter_missingGatewayKey_returns401() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", null, "1", "ROLE_USER", null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_invalidGatewayKey_returns401() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", "wrong-key", "1", "ROLE_USER", null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_validGatewayKeyInternalRoute_proceedsWithoutAuth() {
        MockServerWebExchange exchange = createExchange("/internal/notifications", TEST_KEY, null, null, null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void filter_validGatewayKeyWithUserHeaders_setsAuthentication() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, "1", "ROLE_USER", null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void filter_validGatewayKeyWithCustomerId_setsDetails() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, "1", "ROLE_USER", "100");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_missingUserId_returns401() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, null, "ROLE_USER", null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_missingRole_returns401() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, "1", null, null);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_invalidCustomerId_returns400() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, "1", "ROLE_USER", "not-a-number");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_blankCustomerId_treatedAsNull() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, "1", "ROLE_USER", "  ");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_rejectResponseIsJson() {
        MockServerWebExchange exchange = createExchange("/api/notifications/me", TEST_KEY, null, null, null);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().get("Content-Type"))
                .contains("application/json");
    }
}
