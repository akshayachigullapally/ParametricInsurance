package org.example.productservice.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductThresholdUpdateRequest{
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

    @NotNull(message="Observation window hours is required")
    @Min(value=1,message="Observation window must be at least 1 hour")
    @Max(value=720,message="Observation window cannot exceed 720 hours")
    private Integer observationWindowHours;

    @NotNull(message="Minimum observation count is required")
    @Min(value=1,message="Minimum observation count must be at least 1")
    @Max(value=1000,message="Minimum observation count cannot exceed 1000")
    private Integer minimumObservationCount;

    @Size(max=1000,message="Trigger description must not exceed 1000 characters")
    private String triggerDescription;

    @Size(max=500,message="Change reason must not exceed 500 characters")
    private String changeReason;
}