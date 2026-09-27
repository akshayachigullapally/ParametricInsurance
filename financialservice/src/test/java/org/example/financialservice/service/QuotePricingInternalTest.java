package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.financialservice.dto.InternalPricingRequest;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class QuotePricingInternalTest{
    private PricingRuleRepository rules;
    private QuoteService service;

    @BeforeEach
    void setUp(){
        rules=mock(PricingRuleRepository.class);
        service=new QuoteService(mock(QuoteRepository.class),rules,
                mock(ProductServiceClient.class),mock(TriggerServiceClient.class),
                mock(NotificationService.class),7);
    }

    private InternalPricingRequest request(){
        return InternalPricingRequest.builder().productId(1L).locationZoneId(4L)
                .sumInsured(new BigDecimal("100000")).termMonths(6).build();
    }

    @Test
    void pricesWithTheActiveRule(){
        when(rules.findAllByProductIdAndStatus(1L,PricingRuleStatus.ACTIVE)).thenReturn(Flux.just(
                PricingRule.builder().id(9L).productId(1L).status(PricingRuleStatus.ACTIVE)
                        .baseAnnualRate(new BigDecimal("0.04")).termFactor(new BigDecimal("1.0"))
                        .locationFactor(new BigDecimal("1.5")).minimumPremium(new BigDecimal("250"))
                        .effectiveFrom(LocalDate.now().minusDays(1)).build()));

        // 100000 x 0.04 x 6/12 x 1.0 x 1.5 = 3000
        StepVerifier.create(service.priceInternal(request()))
                .assertNext(price->{
                    assertThat(price.getPricingRuleId()).isEqualTo(9L);
                    assertThat(price.getPremiumAmount()).isEqualByComparingTo("3000.00");
                })
                .verifyComplete();
    }

    @Test
    void failsWhenNoActiveRule(){
        when(rules.findAllByProductIdAndStatus(1L,PricingRuleStatus.ACTIVE)).thenReturn(Flux.empty());

        StepVerifier.create(service.priceInternal(request()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void validatesInput(){
        StepVerifier.create(service.priceInternal(InternalPricingRequest.builder().productId(1L).build()))
                .expectError(ValidationException.class)
                .verify();
    }
}
