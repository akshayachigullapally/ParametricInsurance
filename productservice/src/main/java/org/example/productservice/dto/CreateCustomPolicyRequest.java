package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCustomPolicyRequest{
    @Size(max=150,message="Name must not exceed 150 characters")
    private String name;

    @Size(max=1000,message="Description must not exceed 1000 characters")
    private String description;

    @NotBlank(message="Category is required")
    private String category;

    @NotNull(message="Sum insured is required")
    @Positive(message="Sum insured must be positive")
    private BigDecimal sumInsured;

    @NotNull(message="Policy term is required")
    @Min(value=1,message="Policy term must be at least 1 month")
    private Integer termMonths;

    @NotNull(message="Location zone ID is required")
    @Positive(message="Location zone ID must be greater than zero")
    private Long locationZoneId;

    @NotBlank(message="Peril code is required")
    @Size(max=50,message="Peril code must not exceed 50 characters")
    private String perilCode;

    @NotBlank(message="Measurement type is required")
    @Size(max=50,message="Measurement type must not exceed 50 characters")
    private String measurementType;

    @NotBlank(message="Measurement unit is required")
    @Size(max=40,message="Measurement unit must not exceed 40 characters")
    private String measurementUnit;

    @NotNull(message="Threshold value is required")
    @Positive(message="Threshold value must be positive")
    private BigDecimal thresholdValue;

    @NotBlank(message="Comparison operator is required")
    @Size(max=40,message="Comparison operator must not exceed 40 characters")
    private String comparisonOperator;

    @NotBlank(message="Aggregation type is required")
    @Size(max=40,message="Aggregation type must not exceed 40 characters")
    private String aggregationType;

    @NotNull(message="Observation window is required")
    @Min(value=1,message="Observation window must be at least 1 hour")
    @Max(value=720,message="Observation window cannot exceed 720 hours")
    private Integer observationWindowHours;

    @NotNull(message="Minimum observation count is required")
    @Min(value=1,message="Minimum observation count must be at least 1")
    private Integer minimumObservationCount;

    @Size(max=1000,message="Trigger description must not exceed 1000 characters")
    private String triggerDescription;
}
