package org.example.financialservice.filter;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class GatewayAuthenticationFilterTest{
    private static final String VALID_KEY="test-gateway-key";
    private GatewayAuthenticationFilter filter;

    @BeforeEach
    void setUp(){
        filter=new GatewayAuthenticationFilter(VALID_KEY);
    }

    private MockServerWebExchange exchange(String path,
                                           String gatewayKey,
                                           String userId,
                                           String user,
                                           String role,
                                           String customerId){
        MockServerHttpRequest request=MockServerHttpRequest
                .get(path)
                .build();

        if(gatewayKey!=null){
            request=MockServerHttpRequest
                    .get(path)
                    .header("X-Gateway-Key",gatewayKey)
                    .build();
        }

        if(userId!=null){
            request=MockServerHttpRequest.get(path)
                    .header("X-Gateway-Key",gatewayKey)
                    .header("X-Authenticated-User-Id",userId)
                    .header("X-Authenticated-User",user)
                    .header("X-Authenticated-Role",role)
                    .build();
        }

        if(customerId!=null){
            request=MockServerHttpRequest.get(path)
                    .header("X-Gateway-Key",gatewayKey)
                    .header("X-Authenticated-User-Id",userId)
                    .header("X-Authenticated-User",user)
                    .header("X-Authenticated-Role",role)
                    .header("X-Authenticated-Customer-Id",customerId)
                    .build();
        }

        return MockServerWebExchange.from(request);
    }

    @Test
    void actuatorInfoBypassesAuth(){
        MockServerWebExchange ex=exchange(
                "/actuator/info",null,null,null,null,null);

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();
    }

    @Test
    void missingGatewayKeyReturns401(){
        MockServerWebExchange ex=MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/financial/quotes").build());

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidGatewayKeyReturns401(){
        MockServerWebExchange ex=exchange(
                "/api/financial/quotes","wrong-key",
                null,null,null,null);

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void externalEndpointMissingIdentityReturns401(){
        MockServerWebExchange ex=exchange(
                "/api/financial/quotes",VALID_KEY,
                null,null,null,null);

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void externalEndpointWithAllHeadersPasses(){
        MockServerWebExchange ex=exchange(
                "/api/financial/quotes",VALID_KEY,
                "5","customer","ROLE_CUSTOMER","5");

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isNull();
    }

    @Test
    void invalidUserIdReturns400(){
        MockServerWebExchange ex=MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/financial/quotes")
                        .header("X-Gateway-Key",VALID_KEY)
                        .header("X-Authenticated-User-Id","not-a-number")
                        .header("X-Authenticated-User","admin")
                        .header("X-Authenticated-Role","ROLE_ADMIN")
                        .build());

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRoleHeaderReturns401(){
        MockServerWebExchange ex=MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/financial/quotes")
                        .header("X-Gateway-Key",VALID_KEY)
                        .header("X-Authenticated-User-Id","5")
                        .header("X-Authenticated-User","customer")
                        .build());

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingUserIdHeaderReturns401(){
        MockServerWebExchange ex=MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/financial/quotes")
                        .header("X-Gateway-Key",VALID_KEY)
                        .header("X-Authenticated-User","customer")
                        .header("X-Authenticated-Role","ROLE_CUSTOMER")
                        .build());

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emptyUserIdHeaderReturns401(){
        MockServerWebExchange ex=MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/financial/quotes")
                        .header("X-Gateway-Key",VALID_KEY)
                        .header("X-Authenticated-User-Id","")
                        .header("X-Authenticated-User","customer")
                        .header("X-Authenticated-Role","ROLE_CUSTOMER")
                        .build());

        StepVerifier.create(filter.filter(ex,chain->Mono.empty()))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
