package org.example.financialservice.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsResponse{
    private Long activePricingRules;
    private Long createdQuotes;
    private Long acceptedQuotes;
    private Long expiredQuotes;
    private Long consumedQuotes;
    private Long pendingPayments;
    private Long successfulPayments;
    private Long failedPayments;
    private Long refundedPayments;
    private BigDecimal totalPremiumCollected;
    private Long pendingPayouts;
    private Long processingPayouts;
    private Long successfulPayouts;
    private Long failedPayouts;
    private BigDecimal totalPayoutAmount;
}