package org.example.financialservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import org.example.financialservice.dto.InternalClaimResponse;
import org.example.financialservice.dto.PaymentResponse;
import org.example.financialservice.dto.PayoutResponse;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.dto.PricingRuleResponse;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.dto.QuoteResponse;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.service.ClaimsServiceClient;
import org.example.financialservice.service.NotificationServiceClient;
import org.example.financialservice.service.ProductServiceClient;
import org.example.financialservice.service.TriggerServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment=SpringBootTest
                .WebEnvironment.RANDOM_PORT
)
@AutoConfigureWebTestClient
class FinancialRoutesIntegrationTest{
    @Autowired
    private WebTestClient webClient;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private TriggerServiceClient triggerServiceClient;

    @MockitoBean
    private ClaimsServiceClient claimsServiceClient;

    @MockitoBean
    private NotificationServiceClient notificationServiceClient;

    private static final String KEY=
            "cityshield-local-gateway-key";

    private static final String ADMIN=
            "X-Gateway-Key: "+KEY+"\r\n"+
                    "X-Authenticated-User-Id: 1\r\n"+
                    "X-Authenticated-User: admin\r\n"+
                    "X-Authenticated-Role: ROLE_ADMIN";

    @BeforeEach
    void setUp(){
        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(InternalProductResponse.builder()
                        .productId(1L)
                        .productCode("P")
                        .status("ACTIVE")
                        .minSumInsured(new BigDecimal("1.00"))
                        .maxSumInsured(new BigDecimal("1000000.00"))
                        .minTermMonths(1)
                        .maxTermMonths(60)
                        .build()));
        when(productServiceClient.requireActiveProduct(anyLong()))
                .thenReturn(Mono.empty());
        when(triggerServiceClient.requireActiveZone(anyLong()))
                .thenReturn(Mono.empty());
        when(triggerServiceClient.markEventProcessed(anyLong()))
                .thenReturn(Mono.empty());
        when(claimsServiceClient.settleClaim(any(),any()))
                .thenReturn(Mono.empty());
        when(claimsServiceClient.findClaim(anyLong()))
                .thenReturn(Mono.just(InternalClaimResponse.builder()
                        .claimId(10L)
                        .claimNumber("CLM-10")
                        .customerId(5L)
                        .policyId(7L)
                        .policyNumber("POL-7")
                        .productId(1L)
                        .triggerEventId(3L)
                        .status("APPROVED")
                        .approvedAmount(new BigDecimal("5000.00"))
                        .build()));
        when(notificationServiceClient.sendNotification(any()))
                .thenReturn(Mono.empty());
    }

    private WebTestClient.RequestHeadersSpec<?> adminHeaders(
            WebTestClient.RequestHeadersSpec<?> spec
    ){
        return spec
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","1")
                .header("X-Authenticated-User","admin")
                .header("X-Authenticated-Role","ROLE_ADMIN");
    }

    private WebTestClient.RequestHeadersSpec<?> customerHeaders(
            WebTestClient.RequestHeadersSpec<?> spec
    ){
        return spec
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","5")
                .header("X-Authenticated-User","customer")
                .header("X-Authenticated-Role","ROLE_CUSTOMER")
                .header("X-Authenticated-Customer-Id","5");
    }

    private WebTestClient.RequestHeadersSpec<?> claimsOfficerHeaders(
            WebTestClient.RequestHeadersSpec<?> spec
    ){
        return spec
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","2")
                .header("X-Authenticated-User","officer")
                .header("X-Authenticated-Role","ROLE_CLAIMS_OFFICER");
    }

    private WebTestClient.RequestHeadersSpec<?> underwriterHeaders(
            WebTestClient.RequestHeadersSpec<?> spec
    ){
        return spec
                .header("X-Gateway-Key",KEY)
                .header("X-Authenticated-User-Id","3")
                .header("X-Authenticated-User","underwriter")
                .header("X-Authenticated-Role","ROLE_UNDERWRITER");
    }

    private void createDefaultRule(Long productId){
        String ruleJson="{\"productId\":"+productId+
                ",\"baseAnnualRate\":0.1"+
                ",\"minimumPremium\":1000.00"+
                ",\"termFactor\":1.0"+
                ",\"locationZoneId\":null"+
                ",\"locationFactor\":1.0"+
                ",\"effectiveFrom\":\"2026-01-01\""+
                ",\"effectiveTo\":null}";

        PricingRuleResponse created=webClient.post()
                .uri("/api/financial/pricing-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers->{
                    headers.set("X-Gateway-Key",KEY);
                    headers.set("X-Authenticated-User-Id","1");
                    headers.set("X-Authenticated-User","admin");
                    headers.set("X-Authenticated-Role","ROLE_ADMIN");
                })
                .bodyValue(ruleJson)
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PricingRuleResponse.class)
                .getResponseBody()
                .blockFirst();

        adminHeaders(webClient.put()
                .uri("/api/financial/pricing-rules/"+
                        created.getId()+"/status")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
        )
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(PricingRuleStatus.ACTIVE.name());
    }

    private void consumeQuote(
            Long productId,
            Long zoneId,
            Long policyId,
            String policyNumber
    ){
        createDefaultRule(productId);

        QuoteResponse quote=customerHeaders(webClient.post()
                .uri("/api/financial/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":"+productId+
                        ",\"locationZoneId\":"+zoneId+
                        ",\"sumInsured\":100000.00"+
                        ",\"termMonths\":12}")
        )
                .exchange()
                .expectStatus().isCreated()
                .returnResult(QuoteResponse.class)
                .getResponseBody()
                .blockFirst();

        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/accept")
        )
                .exchange()
                .expectStatus().isOk();

        webClient.put()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/consume")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"policyId\":"+policyId+
                        ",\"policyNumber\":\""+policyNumber+"\"}")
                .exchange()
                .expectStatus().isOk();
    }

    // ---- Pricing rules (Phase B) ----

    @Test
    void pricingRuleLifecycle(){
        String ruleJson="{\"productId\":101"+
                ",\"baseAnnualRate\":0.1"+
                ",\"minimumPremium\":1000.00"+
                ",\"termFactor\":1.0"+
                ",\"locationZoneId\":null"+
                ",\"locationFactor\":1.0"+
                ",\"effectiveFrom\":\"2026-01-01\""+
                ",\"effectiveTo\":null}";

        Long firstId=adminHeaders(webClient.post()
                .uri("/api/financial/pricing-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(ruleJson))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PricingRuleResponse.class)
                .getResponseBody()
                .blockFirst()
                .getId();

        adminHeaders(webClient.get()
                .uri("/api/financial/pricing-rules"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status")
                .isEqualTo(PricingRuleStatus.DRAFT.name());

        adminHeaders(webClient.get()
                .uri("/api/financial/pricing-rules/"+firstId))
                .exchange()
                .expectStatus().isOk();

        // Activate
        adminHeaders(webClient.put()
                .uri("/api/financial/pricing-rules/"+
                        firstId+"/status")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
        )
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(PricingRuleStatus.ACTIVE.name());

        // Activating a second default rule replaces the first
        Long duplicateId=adminHeaders(webClient.post()
                .uri("/api/financial/pricing-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(ruleJson))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PricingRuleResponse.class)
                .getResponseBody()
                .blockFirst()
                .getId();

        adminHeaders(webClient.put()
                .uri("/api/financial/pricing-rules/"+
                        duplicateId+"/status")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"ACTIVE\"}")
        )
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(PricingRuleStatus.ACTIVE.name());

        adminHeaders(webClient.get()
                .uri("/api/financial/pricing-rules/"+firstId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(PricingRuleStatus.INACTIVE.name());

        // Edit active rule blocked
        adminHeaders(webClient.put()
                .uri("/api/financial/pricing-rules/"+duplicateId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(ruleJson)
        )
                .exchange()
                .expectStatus().isEqualTo(409);

        // Archive active rule blocked
        adminHeaders(webClient.delete()
                .uri("/api/financial/pricing-rules/"+duplicateId))
                .exchange()
                .expectStatus().isEqualTo(409);

        // Deactivate then archive
        adminHeaders(webClient.put()
                .uri("/api/financial/pricing-rules/"+
                        firstId+"/status")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"INACTIVE\"}")
        )
                .exchange()
                .expectStatus().isOk();

        adminHeaders(webClient.delete()
                .uri("/api/financial/pricing-rules/"+firstId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(PricingRuleStatus.ARCHIVED.name());
    }

    @Test
    void pricingRuleValidationRejectsUpperBound(){
        String badJson="{\"productId\":102"+
                ",\"baseAnnualRate\":1.5"+
                ",\"minimumPremium\":1000.00"+
                ",\"termFactor\":1.0"+
                ",\"locationZoneId\":null"+
                ",\"locationFactor\":1.0"+
                ",\"effectiveFrom\":\"2026-01-01\"}";

        adminHeaders(webClient.post()
                .uri("/api/financial/pricing-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(badJson))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ---- Quotes (Phase C) ----

    @Test
    void quoteLifecycleAndInternalOperations(){
        createDefaultRule(201L);

        QuoteResponse quote=customerHeaders(webClient.post()
                .uri("/api/financial/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":201"+
                        ",\"locationZoneId\":2"+
                        ",\"sumInsured\":100000.00"+
                        ",\"termMonths\":12}")
        )
                .exchange()
                .expectStatus().isCreated()
                .returnResult(QuoteResponse.class)
                .getResponseBody()
                .blockFirst();

        // /me lists own quote
        customerHeaders(webClient.get()
                .uri("/api/financial/quotes/me"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(quote.getId());

        // Accept
        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/accept"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(QuoteStatus.ACCEPTED.name());

        // Internal validate exact values
        webClient.post()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"customerId\":5"+
                        ",\"productId\":201"+
                        ",\"locationZoneId\":2"+
                        ",\"sumInsured\":100000.00"+
                        ",\"termMonths\":12}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.valid").isEqualTo(true);

        // Internal validate mismatched values
        webClient.post()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"customerId\":5"+
                        ",\"productId\":201"+
                        ",\"locationZoneId\":2"+
                        ",\"sumInsured\":99999.00"+
                        ",\"termMonths\":12}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.valid").isEqualTo(false);

        // Consume
        webClient.put()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/consume")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"policyId\":70"+
                        ",\"policyNumber\":\"POL-70\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(QuoteStatus.CONSUMED.name());

        // Idempotent consume same policy
        webClient.put()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/consume")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"policyId\":70"+
                        ",\"policyNumber\":\"POL-70\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyId").isEqualTo(70);

        // Consume different policy rejected
        webClient.put()
                .uri("/internal/financial/quotes/"+
                        quote.getId()+"/consume")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"policyId\":99"+
                        ",\"policyNumber\":\"POL-99\"}")
                .exchange()
                .expectStatus().isEqualTo(409);

        // Consumed quote cannot be accepted
        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/accept"))
                .exchange()
                .expectStatus().isEqualTo(409);

        // Consumed quote cannot be cancelled
        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/cancel"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void quoteCancellation(){
        createDefaultRule(202L);

        QuoteResponse quote=customerHeaders(webClient.post()
                .uri("/api/financial/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"productId\":202"+
                        ",\"locationZoneId\":2"+
                        ",\"sumInsured\":100000.00"+
                        ",\"termMonths\":12}")
        )
                .exchange()
                .expectStatus().isCreated()
                .returnResult(QuoteResponse.class)
                .getResponseBody()
                .blockFirst();

        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/cancel"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(QuoteStatus.CANCELLED.name());

        // Cancelled quote cannot be accepted
        customerHeaders(webClient.put()
                .uri("/api/financial/quotes/"+
                        quote.getId()+"/accept"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    // ---- Payments (Phase D) ----

    @Test
    void paymentLifecycle(){
        consumeQuote(301L,2L,7L,"POL-7");

        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(InternalPolicyResponse.builder()
                        .id(7L)
                        .policyNumber("POL-7")
                        .customerId(5L)
                        .productId(301L)
                        .status("ACTIVE")
                        .premiumAmount(new BigDecimal("10000.00"))
                        .build()));

        PaymentResponse payment=customerHeaders(webClient.post()
                .uri("/api/financial/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":7,"+
                        "\"method\":\"MOCK_CARD\"}"))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PaymentResponse.class)
                .getResponseBody()
                .blockFirst();

        customerHeaders(webClient.get()
                .uri("/api/financial/payments/me"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].amount").isEqualTo(10000.0);

        customerHeaders(webClient.get()
                .uri("/api/financial/payments/"+
                        payment.getId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.policyNumber").isEqualTo("POL-7");

        // Checkout settles immediately: no admin approval step
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getExternalReference()).startsWith("TXN-");

        // Internal policy status
        webClient.get()
                .uri("/internal/financial/payments/policy/7/status")
                .header("X-Gateway-Key",KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true);

        // Duplicate payment rejected
        customerHeaders(webClient.post()
                .uri("/api/financial/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":7,"+
                        "\"method\":\"MOCK_CARD\"}"))
                .exchange()
                .expectStatus().isEqualTo(409);

        // Refund
        adminHeaders(webClient.put()
                .uri("/api/financial/payments/"+
                        payment.getId()+"/refund"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("REFUNDED");

        // Customer denied admin list
        customerHeaders(webClient.get()
                .uri("/api/financial/payments/admin/all"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void paymentIsPaidWithoutAdminApproval(){
        consumeQuote(302L,2L,8L,"POL-8");

        when(productServiceClient.findPolicy(8L))
                .thenReturn(Mono.just(InternalPolicyResponse.builder()
                        .id(8L)
                        .policyNumber("POL-8")
                        .customerId(5L)
                        .productId(302L)
                        .status("ACTIVE")
                        .premiumAmount(new BigDecimal("10000.00"))
                        .build()));

        customerHeaders(webClient.post()
                .uri("/api/financial/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"policyId\":8,"+
                        "\"method\":\"MOCK_UPI\"}"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.method").isEqualTo("MOCK_UPI")
                .jsonPath("$.completedAt").isNotEmpty();

        webClient.get()
                .uri("/internal/financial/payments/policy/8/status")
                .header("X-Gateway-Key",KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true);
    }

    // ---- Payouts (Phase E) ----

    @Test
    void payoutLifecycle(){
        PayoutResponse payout=claimsOfficerHeaders(webClient.post()
                .uri("/api/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":10}"))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PayoutResponse.class)
                .getResponseBody()
                .blockFirst();

        // Duplicate payout rejected
        claimsOfficerHeaders(webClient.post()
                .uri("/api/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":10}"))
                .exchange()
                .expectStatus().isEqualTo(409);

        // Customer can view own payout
        customerHeaders(webClient.get()
                .uri("/api/financial/payouts/me"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].amount").isEqualTo(5000.0);

        customerHeaders(webClient.get()
                .uri("/api/financial/payouts/"+payout.getId()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.claimNumber").isEqualTo("CLM-10");

        // Paid straight away, no admin step
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);
        assertThat(payout.getExternalReference()).startsWith("AUTO-");

        // Internal by claim
        webClient.get()
                .uri("/internal/financial/payouts/claim/10")
                .header("X-Gateway-Key",KEY)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].status").isEqualTo("SUCCESS");

        // Underwriter denied from payout creation
        underwriterHeaders(webClient.post()
                .uri("/api/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":10}"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void payoutIsPaidAutomaticallyAndCannotBeRetried(){
        when(claimsServiceClient.findClaim(11L))
                .thenReturn(Mono.just(InternalClaimResponse.builder()
                        .claimId(11L)
                        .claimNumber("CLM-11")
                        .customerId(5L)
                        .policyId(9L)
                        .policyNumber("POL-9")
                        .productId(1L)
                        .triggerEventId(4L)
                        .status("APPROVED")
                        .approvedAmount(new BigDecimal("2500.00"))
                        .build()));

        PayoutResponse payout=adminHeaders(webClient.post()
                .uri("/api/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("{\"claimId\":11}"))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(PayoutResponse.class)
                .getResponseBody()
                .blockFirst();

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCESS);

        // Only failed payouts can be retried
        adminHeaders(webClient.put()
                .uri("/api/financial/payouts/"+
                        payout.getId()+"/retry"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void internalPayoutCreationUsesOnlyGatewayKey(){
        when(claimsServiceClient.findClaim(12L))
                .thenReturn(Mono.just(InternalClaimResponse.builder()
                        .claimId(12L)
                        .claimNumber("CLM-12")
                        .customerId(5L)
                        .policyId(10L)
                        .policyNumber("POL-10")
                        .productId(1L)
                        .triggerEventId(5L)
                        .status("APPROVED")
                        .approvedAmount(new BigDecimal("900.00"))
                        .build()));

        webClient.post()
                .uri("/internal/financial/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("X-Gateway-Key",KEY)
                .bodyValue("{\"claimId\":12}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SUCCESS");
    }

    // ---- Metrics (Phase H) ----

    @Test
    void metricsAdminOnly(){
        customerHeaders(webClient.get()
                .uri("/api/financial/admin/metrics"))
                .exchange()
                .expectStatus().isForbidden();

        underwriterHeaders(webClient.get()
                .uri("/api/financial/admin/metrics"))
                .exchange()
                .expectStatus().isForbidden();

        adminHeaders(webClient.get()
                .uri("/api/financial/admin/metrics"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.activePricingRules").exists()
                .jsonPath("$.createdQuotes").exists()
                .jsonPath("$.acceptedQuotes").exists()
                .jsonPath("$.expiredQuotes").exists()
                .jsonPath("$.consumedQuotes").exists()
                .jsonPath("$.pendingPayments").exists()
                .jsonPath("$.successfulPayments").exists()
                .jsonPath("$.failedPayments").exists()
                .jsonPath("$.refundedPayments").exists()
                .jsonPath("$.totalPremiumCollected").exists()
                .jsonPath("$.pendingPayouts").exists()
                .jsonPath("$.processingPayouts").exists()
                .jsonPath("$.successfulPayouts").exists()
                .jsonPath("$.failedPayouts").exists()
                .jsonPath("$.totalPayoutAmount").exists();
    }

    // ---- Misc role coverage ----

    @Test
    void underwriterDeniedPricingRules(){
        underwriterHeaders(webClient.get()
                .uri("/api/financial/pricing-rules"))
                .exchange()
                .expectStatus().isForbidden();

        customerHeaders(webClient.get()
                .uri("/api/financial/pricing-rules"))
                .exchange()
                .expectStatus().isForbidden();
    }
}