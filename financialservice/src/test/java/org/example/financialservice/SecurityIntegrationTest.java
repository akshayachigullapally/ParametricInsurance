package org.example.financialservice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment=SpringBootTest
                .WebEnvironment.RANDOM_PORT,
        properties="spring.cloud.loadbalancer.enabled=false"
)
@AutoConfigureWebTestClient
class SecurityIntegrationTest{
    @Autowired
    private WebTestClient webClient;

    private static final String KEY="cityshield-local-gateway-key";

    @Test
    void invalidGatewayKeyIsRejectedWithNoBasicChallenge(){
        webClient.get()
                .uri("/api/financial/pricing-rules")
                .header("X-Gateway-Key","wrong-key")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader()
                .doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void validGatewayKeyWithoutTrustedHeadersIsRejected(){
        webClient.get()
                .uri("/api/financial/pricing-rules")
                .header("X-Gateway-Key",KEY)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader()
                .doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("Missing trusted identity headers");
    }

    @Test
    void bearerJwtHeaderIsNotTrusted(){
        webClient.get()
                .uri("/api/financial/pricing-rules")
                .header("X-Gateway-Key",KEY)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer bogus.jwt.token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader()
                .doesNotExist(HttpHeaders.WWW_AUTHENTICATE)
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("Missing trusted identity headers");
    }

    @Test
    void customerIsDeniedFromAdminApi(){
        webClient.get()
                .uri("/api/financial/pricing-rules")
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","5")
                .header("X-Authenticated-User","customer")
                .header("X-Authenticated-Role","ROLE_CUSTOMER")
                .header("X-Authenticated-Customer-Id","5")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader()
                .doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
    }

    @Test
    void internalEndpointWorksWithOnlyGatewayKey(){
        webClient.get()
                .uri("/internal/financial/payments/policy/999/status")
                .header("X-Gateway-Key",KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyId").isEqualTo(999)
                .jsonPath("$.successful").isEqualTo(false);
    }

    @Test
    void invalidUserHeaderIsBadRequest(){
        webClient.get()
                .uri("/api/financial/pricing-rules")
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","not-a-number")
                .header("X-Authenticated-User","admin")
                .header("X-Authenticated-Role","ROLE_ADMIN")
                .exchange()
                .expectStatus().isBadRequest();
    }
}