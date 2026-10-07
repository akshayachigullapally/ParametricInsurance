package org.example.financialservice.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.dto.PricingRuleResponse;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.service.PricingRuleService;
import org.example.financialservice.util.RequestValidationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PricingRuleHandlerTest{
    private WebTestClient webClient;
    private PricingRuleService pricingRuleService;

    @BeforeEach
    void setUp(){
        pricingRuleService=
                org.mockito.Mockito.mock(PricingRuleService.class);
        RequestValidationUtil validation=
                org.mockito.Mockito.mock(RequestValidationUtil.class);

        when(validation.validate(any())).thenAnswer(inv->inv.getArgument(0));

        PricingRuleHandler handler=new PricingRuleHandler(
                pricingRuleService,validation);

        org.springframework.web.reactive.function.server.RouterFunction<
                org.springframework.web.reactive.function.server.ServerResponse
                > route=org.springframework.web.reactive.function.server.RouterFunctions
                .route()
                .POST("/api/financial/pricing-rules",handler::create)
                .GET("/api/financial/pricing-rules",handler::findAll)
                .GET("/api/financial/pricing-rules/{ruleId}",handler::findById)
                .PUT("/api/financial/pricing-rules/{ruleId}",handler::update)
                .PUT("/api/financial/pricing-rules/{ruleId}/status",
                        handler::updateStatus)
                .DELETE("/api/financial/pricing-rules/{ruleId}",handler::archive)
                .build();

        webClient=WebTestClient.bindToRouterFunction(route).build();
    }

    private PricingRuleResponse sampleRule(Long id){
        return PricingRuleResponse.builder()
                .id(id)
                .productId(1L)
                .baseAnnualRate(new BigDecimal("0.100000"))
                .minimumPremium(new BigDecimal("1000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(null)
                .locationFactor(new BigDecimal("1.0000"))
                .status(PricingRuleStatus.DRAFT)
                .effectiveFrom(LocalDate.of(2026,1,1))
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void updateReturnsUpdatedRule(){
        when(pricingRuleService.update(anyLong(),any()))
                .thenReturn(Mono.just(sampleRule(1L)));

        webClient.put()
                .uri("/api/financial/pricing-rules/1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":1,"+
                        "\"baseAnnualRate\":0.1,"+
                        "\"minimumPremium\":1000.00,"+
                        "\"termFactor\":1.0,"+
                        "\"locationFactor\":1.0,"+
                        "\"effectiveFrom\":\"2026-01-01\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1);
    }
}
