package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyRequest{
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;

    @NotNull(message="Sum insured is required")
    @Positive(message="Sum insured must be positive")
    private BigDecimal sumInsured;

    @NotNull(message="Policy term is required")
    @Min(value=1,message="Policy term must be at least 1 month")
    private Integer termMonths;

    @NotNull(message="Location zone ID is required")
    @Positive(message="Location zone ID must be greater than zero")
    private Long locationZoneId;

    private String perilCode;
    private String triggerMetric;
    private String triggerOperator;
    private BigDecimal triggerThreshold;
    private Integer triggerDurationHours;
    private String triggerAggregationType;
    private Integer triggerMinObservationCount;
}
