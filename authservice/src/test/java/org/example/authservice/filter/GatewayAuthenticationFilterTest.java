package org.example.authservice.filter;

import org.example.authservice.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest{
    private static final String KEY="test-gateway-key";

    private final GatewayAuthenticationFilter filter=new GatewayAuthenticationFilter(KEY);

    /** Records whether the chain ran and which authentication it saw. */
    private static final class RecordingChain implements WebFilterChain{
        final AtomicBoolean called=new AtomicBoolean(false);
        final AtomicReference<Authentication> authentication=new AtomicReference<>();

        @Override
        public Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange){
            called.set(true);
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .doOnNext(authentication::set)
                    .then();
        }
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request){
        return MockServerWebExchange.from(request);
    }

    @Test
    void missingGatewayKey_shouldReturnForbidden(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users/me"));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body).contains("Request must pass through API Gateway"))
                .verifyComplete();
    }

    @Test
    void gatewayKeyWithoutIdentity_shouldContinueUnauthenticated(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        assertThat(chain.called).isTrue();
        assertThat(chain.authentication.get()).isNull();
    }

    @Test
    void validIdentity_shouldAuthenticateAndPrefixRole(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"5")
                .header(GatewayHeaders.USER_EMAIL,"a@b.com")
                .header(GatewayHeaders.USER_ROLE,"CUSTOMER")
                .header(GatewayHeaders.CUSTOMER_ID,"9"));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        Authentication authentication=chain.authentication.get();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(5L);
        assertThat(authentication.getDetails()).isEqualTo("a@b.com");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CUSTOMER");
        assertThat((Long)exchange.getAttribute(GatewayHeaders.AUTHENTICATED_USER_ID)).isEqualTo(5L);
        assertThat((Long)exchange.getAttribute(GatewayHeaders.AUTHENTICATED_CUSTOMER_ID)).isEqualTo(9L);
    }

    @Test
    void roleAlreadyPrefixed_shouldBeKept(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"1")
                .header(GatewayHeaders.USER_EMAIL,"admin@b.com")
                .header(GatewayHeaders.USER_ROLE,"ROLE_ADMIN"));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        assertThat(chain.authentication.get().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void nonNumericUserId_shouldReturnForbidden(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"abc")
                .header(GatewayHeaders.USER_EMAIL,"a@b.com")
                .header(GatewayHeaders.USER_ROLE,"CUSTOMER"));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonNumericCustomerId_shouldReturnForbidden(){
        RecordingChain chain=new RecordingChain();
        MockServerWebExchange exchange=exchange(MockServerHttpRequest.get("/api/users/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.CUSTOMER_ID,"x1"));

        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();

        assertThat(chain.called).isFalse();
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body).contains("Invalid authenticated customer id"))
                .verifyComplete();
    }
}
