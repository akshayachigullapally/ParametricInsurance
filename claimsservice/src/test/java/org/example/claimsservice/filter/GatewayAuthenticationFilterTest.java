package org.example.claimsservice.filter;

import org.example.claimsservice.util.GatewayHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest{
    private static final String KEY="test-gateway-key";

    private final GatewayAuthenticationFilter filter=new GatewayAuthenticationFilter(KEY);

    private static final class RecordingChain implements WebFilterChain{
        final AtomicBoolean called=new AtomicBoolean(false);
        final AtomicReference<Authentication> authentication=new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange){
            called.set(true);
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .doOnNext(authentication::set)
                    .then();
        }
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request,RecordingChain chain){
        MockServerWebExchange exchange=MockServerWebExchange.from(request);
        StepVerifier.create(filter.filter(exchange,chain)).verifyComplete();
        return exchange;
    }

    @Test
    void actuatorPaths_shouldSkipGatewayCheck(){
        RecordingChain health=new RecordingChain();
        RecordingChain info=new RecordingChain();

        run(MockServerHttpRequest.get("/actuator/health"),health);
        run(MockServerHttpRequest.get("/actuator/info"),info);

        assertThat(health.called).isTrue();
        assertThat(info.called).isTrue();
    }

    @Test
    void wrongGatewayKey_shouldReturnForbidden(){
        RecordingChain chain=new RecordingChain();

        MockServerWebExchange exchange=run(MockServerHttpRequest.get("/api/claims/me")
                .header(GatewayHeaders.GATEWAY_KEY,"wrong"),chain);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        StepVerifier.create(exchange.getResponse().getBodyAsString())
                .assertNext(body->assertThat(body).contains("Request must pass through API Gateway"))
                .verifyComplete();
    }

    @Test
    void internalCallWithoutIdentity_shouldContinueUnauthenticated(){
        RecordingChain chain=new RecordingChain();

        run(MockServerHttpRequest.get("/internal/claims/1")
                .header(GatewayHeaders.GATEWAY_KEY,KEY),chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.authentication.get()).isNull();
    }

    @Test
    void blankRole_shouldContinueUnauthenticated(){
        RecordingChain chain=new RecordingChain();

        run(MockServerHttpRequest.get("/api/claims/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"5")
                .header(GatewayHeaders.USER_EMAIL,"a@b.com")
                .header(GatewayHeaders.USER_ROLE," "),chain);

        assertThat(chain.called).isTrue();
        assertThat(chain.authentication.get()).isNull();
    }

    @Test
    void validIdentity_shouldAuthenticateWithPrefixedRole(){
        RecordingChain chain=new RecordingChain();

        run(MockServerHttpRequest.get("/api/claims/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"5")
                .header(GatewayHeaders.USER_EMAIL,"a@b.com")
                .header(GatewayHeaders.USER_ROLE,"CLAIMS_OFFICER"),chain);

        Authentication authentication=chain.authentication.get();
        assertThat(authentication.getPrincipal()).isEqualTo(5L);
        assertThat(authentication.getDetails()).isEqualTo("a@b.com");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CLAIMS_OFFICER");
    }

    @Test
    void prefixedRole_shouldBeKept(){
        RecordingChain chain=new RecordingChain();

        run(MockServerHttpRequest.get("/api/claims/admin/all")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"1")
                .header(GatewayHeaders.USER_EMAIL,"admin@b.com")
                .header(GatewayHeaders.USER_ROLE,"ROLE_ADMIN"),chain);

        assertThat(chain.authentication.get().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void nonNumericUserId_shouldReturnForbidden(){
        RecordingChain chain=new RecordingChain();

        MockServerWebExchange exchange=run(MockServerHttpRequest.get("/api/claims/me")
                .header(GatewayHeaders.GATEWAY_KEY,KEY)
                .header(GatewayHeaders.USER_ID,"abc")
                .header(GatewayHeaders.USER_EMAIL,"a@b.com")
                .header(GatewayHeaders.USER_ROLE,"CUSTOMER"),chain);

        assertThat(chain.called).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
