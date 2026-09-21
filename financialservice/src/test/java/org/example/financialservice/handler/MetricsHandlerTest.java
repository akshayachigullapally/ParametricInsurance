package org.example.financialservice.handler;

import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import org.example.financialservice.dto.MetricsResponse;
import org.example.financialservice.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class MetricsHandlerTest{
    private WebTestClient webClient;
    private MetricsService metricsService;

    @BeforeEach
    void setUp(){
        metricsService=
                org.mockito.Mockito.mock(MetricsService.class);

        MetricsHandler handler=new MetricsHandler(metricsService);

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > route=org.springframework.web.reactive.function.server.RouterFunctions
                .route()
                .GET("/api/financial/admin/metrics",handler::metrics)
                .build();

        webClient=WebTestClient.bindToRouterFunction(route).build();
    }

    private MetricsResponse sampleMetrics(){
        return MetricsResponse.builder()
                .activePricingRules(5L)
                .createdQuotes(100L)
                .acceptedQuotes(80L)
                .expiredQuotes(10L)
                .consumedQuotes(50L)
                .pendingPayments(20L)
                .successfulPayments(60L)
                .failedPayments(5L)
                .refundedPayments(2L)
                .totalPremiumCollected(new BigDecimal("500000.00"))
                .pendingPayouts(3L)
                .processingPayouts(2L)
                .successfulPayouts(15L)
                .failedPayouts(1L)
                .totalPayoutAmount(new BigDecimal("75000.00"))
                .build();
    }

    @Test
    void metricsReturnsAllFields(){
        when(metricsService.metrics())
                .thenReturn(Mono.just(sampleMetrics()));

        webClient.get()
                .uri("/api/financial/admin/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activePricingRules").isEqualTo(5)
                .jsonPath("$.createdQuotes").isEqualTo(100)
                .jsonPath("$.acceptedQuotes").isEqualTo(80)
                .jsonPath("$.expiredQuotes").isEqualTo(10)
                .jsonPath("$.consumedQuotes").isEqualTo(50)
                .jsonPath("$.pendingPayments").isEqualTo(20)
                .jsonPath("$.successfulPayments").isEqualTo(60)
                .jsonPath("$.failedPayments").isEqualTo(5)
                .jsonPath("$.refundedPayments").isEqualTo(2)
                .jsonPath("$.totalPremiumCollected").isEqualTo(500000.00)
                .jsonPath("$.pendingPayouts").isEqualTo(3)
                .jsonPath("$.processingPayouts").isEqualTo(2)
                .jsonPath("$.successfulPayouts").isEqualTo(15)
                .jsonPath("$.failedPayouts").isEqualTo(1)
                .jsonPath("$.totalPayoutAmount").isEqualTo(75000.00);
    }
}
