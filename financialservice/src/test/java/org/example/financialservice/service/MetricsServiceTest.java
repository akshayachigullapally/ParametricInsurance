package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import org.example.financialservice.TestAuth;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class MetricsServiceTest{
    @Test
    void metricsAggregatesAllValues(){
        PricingRuleRepository pricingRuleRepository=
                mock(PricingRuleRepository.class);
        QuoteRepository quoteRepository=mock(QuoteRepository.class);
        PaymentRepository paymentRepository=
                mock(PaymentRepository.class);
        PayoutRepository payoutRepository=
                mock(PayoutRepository.class);

        when(pricingRuleRepository.countByStatus(
                PricingRuleStatus.ACTIVE
        )).thenReturn(Mono.just(3L));
        when(quoteRepository.count()).thenReturn(Mono.just(10L));
        when(quoteRepository.countByStatus(
                QuoteStatus.ACCEPTED
        )).thenReturn(Mono.just(4L));
        when(quoteRepository.countByStatus(
                QuoteStatus.EXPIRED
        )).thenReturn(Mono.just(2L));
        when(quoteRepository.countByStatus(
                QuoteStatus.CONSUMED
        )).thenReturn(Mono.just(3L));
        when(paymentRepository.countByStatus(
                PaymentStatus.PENDING
        )).thenReturn(Mono.just(5L));
        when(paymentRepository.countByStatus(
                PaymentStatus.SUCCESS
        )).thenReturn(Mono.just(4L));
        when(paymentRepository.countByStatus(
                PaymentStatus.FAILED
        )).thenReturn(Mono.just(1L));
        when(paymentRepository.countByStatus(
                PaymentStatus.REFUNDED
        )).thenReturn(Mono.just(2L));
        when(paymentRepository.sumSuccessfulAmount())
                .thenReturn(Mono.just(
                        new BigDecimal("40000.00")
                ));
        when(payoutRepository.countByStatus(
                PayoutStatus.PENDING
        )).thenReturn(Mono.just(2L));
        when(payoutRepository.countByStatus(
                PayoutStatus.PROCESSING
        )).thenReturn(Mono.just(1L));
        when(payoutRepository.countByStatus(
                PayoutStatus.SUCCESS
        )).thenReturn(Mono.just(3L));
        when(payoutRepository.countByStatus(
                PayoutStatus.FAILED
        )).thenReturn(Mono.just(1L));
        when(payoutRepository.sumSuccessfulAmount())
                .thenReturn(Mono.just(
                        new BigDecimal("15000.00")
                ));

        MetricsService service=new MetricsService(
                pricingRuleRepository,
                quoteRepository,
                paymentRepository,
                payoutRepository
        );

        StepVerifier.create(TestAuth.admin(
                service.metrics(),
                1L
        ))
                .assertNext(metrics->{
                    assertThat(metrics.getActivePricingRules())
                            .isEqualTo(3L);
                    assertThat(metrics.getCreatedQuotes())
                            .isEqualTo(10L);
                    assertThat(metrics.getAcceptedQuotes())
                            .isEqualTo(4L);
                    assertThat(metrics.getExpiredQuotes())
                            .isEqualTo(2L);
                    assertThat(metrics.getConsumedQuotes())
                            .isEqualTo(3L);
                    assertThat(metrics.getPendingPayments())
                            .isEqualTo(5L);
                    assertThat(metrics.getSuccessfulPayments())
                            .isEqualTo(4L);
                    assertThat(metrics.getFailedPayments())
                            .isEqualTo(1L);
                    assertThat(metrics.getRefundedPayments())
                            .isEqualTo(2L);
                    assertThat(metrics.getTotalPremiumCollected())
                            .isEqualByComparingTo(
                                    new BigDecimal("40000.00")
                            );
                    assertThat(metrics.getPendingPayouts())
                            .isEqualTo(2L);
                    assertThat(metrics.getProcessingPayouts())
                            .isEqualTo(1L);
                    assertThat(metrics.getSuccessfulPayouts())
                            .isEqualTo(3L);
                    assertThat(metrics.getFailedPayouts())
                            .isEqualTo(1L);
                    assertThat(metrics.getTotalPayoutAmount())
                            .isEqualByComparingTo(
                                    new BigDecimal("15000.00")
                            );
                })
                .verifyComplete();
    }
}