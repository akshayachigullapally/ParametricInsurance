package org.example.triggerservice.filter;

import org.example.triggerservice.util.GatewayHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayAuthenticationFilterTest {

    @InjectMocks
    private GatewayAuthenticationFilter gatewayAuthenticationFilter;

    @BeforeEach
    void setUp() {
        // @Value is not populated by Mockito's @InjectMocks; the production filter
        // relies on Spring to inject this field, so set it directly for the unit test.
        ReflectionTestUtils.setField(gatewayAuthenticationFilter, "internalKey", "test-gateway-key");
    }

    private WebFilterChain createChain(boolean called) {
        WebFilterChain chain = mock(WebFilterChain.class);
        lenient().when(chain.filter(any(ServerWebExchange.class))).thenReturn(called ? Mono.empty() : Mono.error(new RuntimeException("Chain not called")));
        return chain;
    }

    @Test
    void filter_shouldAllowActuatorHealthPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(true);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_shouldAllowActuatorHealthSubPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health/details").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(true);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_shouldAllowActuatorInfoPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/info").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(true);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_shouldReturnForbiddenForMissingGatewayKey() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/triggers/events").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(false);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_shouldReturnForbiddenForInvalidGatewayKey() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/triggers/events")
                .header(GatewayHeaders.GATEWAY_KEY, "wrong-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(false);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void filter_shouldAllowInternalTriggersPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/internal/triggers/health")
                .header(GatewayHeaders.GATEWAY_KEY, "test-gateway-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(true);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_shouldReturnUnauthorizedForMissingUserHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/triggers/events")
                .header(GatewayHeaders.GATEWAY_KEY, "test-gateway-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(false);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filter_shouldAuthenticateWithValidHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/triggers/events")
                .header(GatewayHeaders.GATEWAY_KEY, "test-gateway-key")
                .header(GatewayHeaders.USER_ID, "1")
                .header(GatewayHeaders.USER_EMAIL, "user@test.com")
                .header(GatewayHeaders.USER_ROLE, "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        WebFilterChain chain = createChain(true);

        StepVerifier.create(gatewayAuthenticationFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }
}
