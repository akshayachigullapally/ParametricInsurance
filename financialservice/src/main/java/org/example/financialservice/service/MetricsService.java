package org.example.financialservice.service;

import java.math.BigDecimal;
import java.util.List;
import org.example.financialservice.dto.MetricsResponse;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.model.PricingRuleStatus;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.PayoutRepository;
import org.example.financialservice.repository.PricingRuleRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.example.financialservice.util.SecurityUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MetricsService{
    private final PricingRuleRepository pricingRuleRepository;
    private final QuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final PayoutRepository payoutRepository;

    public MetricsService(
            PricingRuleRepository pricingRuleRepository,
            QuoteRepository quoteRepository,
            PaymentRepository paymentRepository,
            PayoutRepository payoutRepository
    ){
        this.pricingRuleRepository=pricingRuleRepository;
        this.quoteRepository=quoteRepository;
        this.paymentRepository=paymentRepository;
        this.payoutRepository=payoutRepository;
    }

    public Mono<MetricsResponse> metrics(){
        return SecurityUtil.requireAdmin()
                .then(Mono.zip(
                        List.of(
                                pricingRuleRepository.countByStatus(
                                        PricingRuleStatus.ACTIVE
                                ),
                                quoteRepository.count(),
                                quoteRepository.countByStatus(
                                        QuoteStatus.ACCEPTED
                                ),
                                quoteRepository.countByStatus(
                                        QuoteStatus.EXPIRED
                                ),
                                quoteRepository.countByStatus(
                                        QuoteStatus.CONSUMED
                                ),
                                paymentRepository.countByStatus(
                                        PaymentStatus.PENDING
                                ),
                                paymentRepository.countByStatus(
                                        PaymentStatus.SUCCESS
                                ),
                                paymentRepository.countByStatus(
                                        PaymentStatus.FAILED
                                ),
                                paymentRepository.countByStatus(
                                        PaymentStatus.REFUNDED
                                ),
                                paymentRepository.sumSuccessfulAmount(),
                                payoutRepository.countByStatus(
                                        PayoutStatus.PENDING
                                ),
                                payoutRepository.countByStatus(
                                        PayoutStatus.PROCESSING
                                ),
                                payoutRepository.countByStatus(
                                        PayoutStatus.SUCCESS
                                ),
                                payoutRepository.countByStatus(
                                        PayoutStatus.FAILED
                                ),
                                payoutRepository.sumSuccessfulAmount()
                        ),
                        array->MetricsResponse.builder()
                                .activePricingRules((Long)array[0])
                                .createdQuotes((Long)array[1])
                                .acceptedQuotes((Long)array[2])
                                .expiredQuotes((Long)array[3])
                                .consumedQuotes((Long)array[4])
                                .pendingPayments((Long)array[5])
                                .successfulPayments((Long)array[6])
                                .failedPayments((Long)array[7])
                                .refundedPayments((Long)array[8])
                                .totalPremiumCollected(
                                        (BigDecimal)array[9]
                                )
                                .pendingPayouts((Long)array[10])
                                .processingPayouts((Long)array[11])
                                .successfulPayouts((Long)array[12])
                                .failedPayouts((Long)array[13])
                                .totalPayoutAmount(
                                        (BigDecimal)array[14]
                                )
                                .build()
                ));
    }
}