package org.example.financialservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPricingResponse{
    private Long pricingRuleId;
    private BigDecimal annualRate;
    private BigDecimal termFactor;
    private BigDecimal locationFactor;
    private BigDecimal minimumPremium;
    private BigDecimal premiumAmount;
}
