package org.example.financialservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.model.PricingRuleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRuleResponse{
    private Long id;
    private Long productId;
    private BigDecimal baseAnnualRate;
    private BigDecimal minimumPremium;
    private BigDecimal termFactor;
    private Long locationZoneId;
    private BigDecimal locationFactor;
    private PricingRuleStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}