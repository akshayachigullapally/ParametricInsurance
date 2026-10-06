package org.example.financialservice.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPricingRequest{
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;
    @NotNull(message="Location zone id is required")
    @Positive(message="Location zone id must be positive")
    private Long locationZoneId;
    @NotNull(message="Sum insured is required")
    @Positive(message="Sum insured must be positive")
    private BigDecimal sumInsured;
    @NotNull(message="Term is required")
    @Min(value=1,message="Term must be at least 1 month")
    @Max(value=120,message="Term cannot exceed 120 months")
    private Integer termMonths;
}
