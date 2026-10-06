package org.example.triggerservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerDefinitionRequest{
    @NotBlank(message="Definition code is required")
    @Size(max=75,message="Definition code cannot exceed 75 characters")
    private String definitionCode;

    @NotBlank(message="Definition name is required")
    @Size(max=150,message="Definition name cannot exceed 150 characters")
    private String definitionName;

    @NotBlank(message="Description is required")
    @Size(max=500,message="Description cannot exceed 500 characters")
    private String description;

    @NotNull(message="Peril ID is required")
    @Positive(message="Peril ID must be greater than zero")
    private Long perilId;

    @NotNull(message="Location zone ID is required")
    @Positive(message="Location zone ID must be greater than zero")
    private Long locationZoneId;

    @NotNull(message="Product ID is required")
    @Positive(message="Product ID must be greater than zero")
    private Long productId;

    @NotBlank(message="Measurement type is required")
    private String measurementType;

    @NotBlank(message="Measurement unit is required")
    private String measurementUnit;

    @NotBlank(message="Aggregation type is required")
    private String aggregationType;

    @NotBlank(message="Comparison operator is required")
    private String comparisonOperator;

    @NotNull(message="Threshold value is required")
    @DecimalMin(value="0.0",inclusive=true,message="Threshold value cannot be negative")
    private BigDecimal thresholdValue;

    @NotNull(message="Observation window is required")
    @Positive(message="Observation window must be greater than zero")
    @Max(value=720,message="Observation window cannot exceed 720 hours")
    private Integer observationWindowHours;

    @NotNull(message="Minimum observation count is required")
    @Positive(message="Minimum observation count must be greater than zero")
    private Integer minimumObservationCount;

    @NotNull(message="Effective start date is required")
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @JsonIgnore
    @AssertTrue(message="Effective end date cannot be before the effective start date")
    public boolean isEffectiveRangeValid(){
        return effectiveFrom==null||effectiveTo==null||!effectiveTo.isBefore(effectiveFrom);
    }
}
