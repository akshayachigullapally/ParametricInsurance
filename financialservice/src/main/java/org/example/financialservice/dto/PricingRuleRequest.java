package org.example.financialservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRuleRequest{
    @NotNull
    @Positive
    private Long productId;

    @NotNull
    @DecimalMin(value="0.000001")
    @DecimalMax(value="1.000000")
    private BigDecimal baseAnnualRate;

    @NotNull
    @DecimalMin(value="0.00")
    private BigDecimal minimumPremium;

    @NotNull
    @DecimalMin(value="0.0001")
    @DecimalMax(value="10.0000")
    private BigDecimal termFactor;

    @Positive
    private Long locationZoneId;

    @NotNull
    @DecimalMin(value="0.0001")
    @DecimalMax(value="10.0000")
    private BigDecimal locationFactor;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}