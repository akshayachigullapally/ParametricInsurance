package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.CreateQuoteRequest;
import org.example.financialservice.dto.InternalQuoteConsumeRequest;
import org.example.financialservice.dto.InternalQuoteValidateRequest;
import org.example.financialservice.dto.InternalProductResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.model.Quote;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class QuoteServiceTest{
    private QuoteRepository quoteRepository;
    private PricingRuleRepository pricingRuleRepository;
    private ProductServiceClient productServiceClient;
    private TriggerServiceClient triggerServiceClient;
    private NotificationService notifications;
    private QuoteService service;

    private static final InternalProductResponse ACTIVE_PRODUCT=
            InternalProductResponse.builder()
                    .productId(1L)
                    .status("ACTIVE")
                    .minSumInsured(new BigDecimal("1.00"))
                    .maxSumInsured(new BigDecimal("1000000.00"))
                    .minTermMonths(1)
                    .maxTermMonths(60)
                    .build();

    @BeforeEach
    void setUp(){
        quoteRepository=mock(QuoteRepository.class);
        pricingRuleRepository=mock(PricingRuleRepository.class);
        productServiceClient=mock(ProductServiceClient.class);
        triggerServiceClient=mock(TriggerServiceClient.class);
        notifications=mock(NotificationService.class);

        when(productServiceClient.findProduct(anyLong()))
                .thenReturn(Mono.just(ACTIVE_PRODUCT));
        when(triggerServiceClient.requireActiveZone(anyLong()))
                .thenReturn(Mono.empty());
        when(notifications.audit(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        )).thenReturn(Mono.empty());
        when(notifications.notifyQuoteCreated(
                any(),any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyQuoteExpired(
                any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyQuoteConsumed(
                any(),any(),any(),any()
        )).thenReturn(Mono.empty());

        service=new QuoteService(
                quoteRepository,
                pricingRuleRepository,
                productServiceClient,
                triggerServiceClient,
                notifications,
                7
        );
    }

    private PricingRule zoneRule(Long productId,Long zoneId){
        return PricingRule.builder()
                .id(1L)
                .productId(productId)
                .baseAnnualRate(new BigDecimal("0.100000"))
                .minimumPremium(new BigDecimal("1000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(zoneId)
                .locationFactor(new BigDecimal("1.0000"))
                .status(PricingRuleStatus.ACTIVE)
                .effectiveFrom(java.time.LocalDate.of(2025,1,1))
                .build();
    }

    private PricingRule defaultRule(Long productId){
        return PricingRule.builder()
                .id(2L)
                .productId(productId)
                .baseAnnualRate(new BigDecimal("0.200000"))
                .minimumPremium(new BigDecimal("2000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(null)
                .locationFactor(new BigDecimal("1.0000"))
                .status(PricingRuleStatus.ACTIVE)
                .effectiveFrom(java.time.LocalDate.of(2025,1,1))
                .build();
    }

    private Quote ownedQuote(Long id){
        return Quote.builder()
                .id(id)
                .quoteReference("QTE-TEST")
                .customerId(5L)
                .productId(1L)
                .locationZoneId(2L)
                .sumInsured(new BigDecimal("100000.00"))
                .termMonths(12)
                .annualRate(new BigDecimal("0.100000"))
                .termFactor(new BigDecimal("1.0000"))
                .locationFactor(new BigDecimal("1.0000"))
                .premiumAmount(new BigDecimal("10000.00"))
                .status(QuoteStatus.CREATED)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void exactZoneRulePreferred(){
        PricingRule zone=zoneRule(1L,2L);
        PricingRule defaultR=defaultRule(1L);

        when(pricingRuleRepository
                .findAllByProductIdAndStatus(
                        1L,
                        PricingRuleStatus.ACTIVE
                )).thenReturn(Flux.just(zone,defaultR));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->
                        Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()),
                5L,
                5L
        ))
                .assertNext(r->assertThat(r.getAnnualRate())
                        .isEqualByComparingTo(
                                new BigDecimal("0.100000")))
                .verifyComplete();
    }

    @Test
    void fallbackDefaultRule(){
        PricingRule defaultR=defaultRule(1L);

        when(pricingRuleRepository
                .findAllByProductIdAndStatus(
                        1L,
                        PricingRuleStatus.ACTIVE
                )).thenReturn(Flux.just(defaultR));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(99L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()),
                5L,
                5L
        ))
                .assertNext(r->assertThat(r.getPremiumAmount())
                        .isEqualByComparingTo(
                                new BigDecimal("20000.00")))
                .verifyComplete();
    }

    @Test
    void premiumMinimumApplied(){
        PricingRule rule=zoneRule(1L,2L);
        rule.setMinimumPremium(new BigDecimal("25000.00"));
        rule.setBaseAnnualRate(new BigDecimal("0.050000"));

        when(pricingRuleRepository
                .findAllByProductIdAndStatus(
                        1L,
                        PricingRuleStatus.ACTIVE
                )).thenReturn(Flux.just(rule));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()),
                5L,
                5L
        ))
                .assertNext(r->assertThat(r.getPremiumAmount())
                        .isEqualByComparingTo(
                                new BigDecimal("25000.00")))
                .verifyComplete();
    }

    @Test
    void premiumCalculation(){
        PricingRule rule=zoneRule(1L,2L);
        rule.setBaseAnnualRate(new BigDecimal("0.100000"));
        rule.setTermFactor(new BigDecimal("1.2000"));
        rule.setLocationFactor(new BigDecimal("1.5000"));
        rule.setMinimumPremium(BigDecimal.ZERO);

        when(pricingRuleRepository
                .findAllByProductIdAndStatus(
                        1L,
                        PricingRuleStatus.ACTIVE
                )).thenReturn(Flux.just(rule));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()),
                5L,
                5L
        ))
                .assertNext(r->{
                    // 100000 * 0.1 * (12/12) * 1.2 * 1.5 = 18000
                    assertThat(r.getPremiumAmount())
                            .isEqualByComparingTo(
                                    new BigDecimal("18000.00"));
                })
                .verifyComplete();
    }

    @Test
    void roundingHalfUp(){
        PricingRule rule=zoneRule(1L,2L);
        rule.setBaseAnnualRate(new BigDecimal("0.100000"));
        rule.setTermFactor(BigDecimal.ONE);
        rule.setLocationFactor(BigDecimal.ONE);
        rule.setMinimumPremium(BigDecimal.ZERO);

        when(pricingRuleRepository
                .findAllByProductIdAndStatus(
                        1L,
                        PricingRuleStatus.ACTIVE
                )).thenReturn(Flux.just(rule));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(1)
                        .build()),
                5L,
                5L
        ))
                .assertNext(r->{
                    // 100000 * 0.1 / 12 = 833.333333 HALF_UP = 833.33
                    assertThat(r.getPremiumAmount())
                            .isEqualByComparingTo(
                                    new BigDecimal("833.33"));
                })
                .verifyComplete();
    }

    @Test
    void expiredQuoteCannotBeAccepted(){
        Quote quote=ownedQuote(1L);
        quote.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(quoteRepository.findByIdAndCustomerId(1L,5L))
                .thenReturn(Mono.just(quote));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.accept(1L),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void cancelledQuoteCannotBeAccepted(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.CANCELLED);

        when(quoteRepository.findByIdAndCustomerId(1L,5L))
                .thenReturn(Mono.just(quote));

        StepVerifier.create(TestAuth.customer(
                service.accept(1L),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void consumedQuoteCannotBeAccepted(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.CONSUMED);
        quote.setPolicyId(7L);

        when(quoteRepository.findByIdAndCustomerId(1L,5L))
                .thenReturn(Mono.just(quote));

        StepVerifier.create(TestAuth.customer(
                service.accept(1L),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void acceptedQuoteCannotBeCancelled(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());

        when(quoteRepository.findByIdAndCustomerId(1L,5L))
                .thenReturn(Mono.just(quote));

        StepVerifier.create(TestAuth.customer(
                service.cancel(1L),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void internalValidateReturnsTrueForValidQuote(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(quote));

        StepVerifier.create(service.validateInternal(
                1L,
                InternalQuoteValidateRequest.builder()
                        .customerId(5L)
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()
        ))
                .assertNext(r->{
                    assertThat(r.isValid()).isTrue();
                    assertThat(r.getValidationReason())
                            .isEqualTo("Quote is valid");
                })
                .verifyComplete();
    }

    @Test
    void internalValidateRejectsWrongCustomer(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(quote));

        StepVerifier.create(service.validateInternal(
                1L,
                InternalQuoteValidateRequest.builder()
                        .customerId(99L)
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("100000.00"))
                        .termMonths(12)
                        .build()
        ))
                .assertNext(r->assertThat(r.isValid()).isFalse())
                .verifyComplete();
    }

    @Test
    void consumeIdempotentSamePolicy(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(quote));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(quoteRepository.findAllByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Flux.empty());

        StepVerifier.create(service.consume(
                1L,
                InternalQuoteConsumeRequest.builder()
                        .policyId(7L)
                        .policyNumber("POL-7")
                        .build()
        ))
                .assertNext(r->assertThat(
                        r.getStatus()
                ).isEqualTo(QuoteStatus.CONSUMED))
                .verifyComplete();

        // Idempotent second consume with same policy
        Quote consumed=ownedQuote(1L);
        consumed.setStatus(QuoteStatus.CONSUMED);
        consumed.setPolicyId(7L);
        consumed.setPolicyNumber("POL-7");

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(consumed));

        StepVerifier.create(service.consume(
                1L,
                InternalQuoteConsumeRequest.builder()
                        .policyId(7L)
                        .policyNumber("POL-7")
                        .build()
        ))
                .assertNext(r->assertThat(r.getPolicyId())
                        .isEqualTo(7L))
                .verifyComplete();
    }

    @Test
    void consumeRejectsDifferentPolicy(){
        Quote quote=ownedQuote(1L);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setAcceptedAt(LocalDateTime.now());

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(quote));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(quoteRepository.findAllByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Flux.empty());

        // First consume with policy 7
        StepVerifier.create(service.consume(
                1L,
                InternalQuoteConsumeRequest.builder()
                        .policyId(7L)
                        .policyNumber("POL-7")
                        .build()
        )).expectNextCount(1).verifyComplete();

        // Second consume with different policy
        Quote consumed=ownedQuote(1L);
        consumed.setStatus(QuoteStatus.CONSUMED);
        consumed.setPolicyId(7L);

        when(quoteRepository.findById(1L))
                .thenReturn(Mono.just(consumed));

        StepVerifier.create(service.consume(
                1L,
                InternalQuoteConsumeRequest.builder()
                        .policyId(99L)
                        .policyNumber("POL-99")
                        .build()
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void productLimitsEnforced(){
        when(productServiceClient.findProduct(1L))
                .thenReturn(Mono.just(ACTIVE_PRODUCT));
        when(pricingRuleRepository.findAllByProductIdAndStatus(
                1L,
                PricingRuleStatus.ACTIVE
        )).thenReturn(Flux.just(defaultRule(1L)));

        // Sum too high
        StepVerifier.create(TestAuth.customer(
                service.create(CreateQuoteRequest.builder()
                        .productId(1L)
                        .locationZoneId(2L)
                        .sumInsured(new BigDecimal("99999999.00"))
                        .termMonths(12)
                        .build()),
                5L,
                5L
        ))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void expiredQuoteMarkedAsExpiredOnAccess(){
        Quote quote=ownedQuote(1L);
        quote.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(quoteRepository.findByIdAndCustomerId(1L,5L))
                .thenReturn(Mono.just(quote));
        when(quoteRepository.save(any(Quote.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.customer(
                service.findMine(1L),
                5L,
                5L
        ))
                .assertNext(r->assertThat(r.getStatus())
                        .isEqualTo(QuoteStatus.EXPIRED))
                .verifyComplete();
    }
}