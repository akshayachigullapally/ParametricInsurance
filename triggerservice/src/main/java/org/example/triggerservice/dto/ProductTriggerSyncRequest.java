package org.example.triggerservice.dto;

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
public class ProductTriggerSyncRequest {
    @NotNull(message="Product id is required")
    @Positive(message="Product id must be positive")
    private Long productId;
    @NotBlank(message="Product code is required")
    @Size(max=50,message="Product code must not exceed 50 characters")
    private String productCode;
    private String productName;
    private String productStatus;
    private String perilCode;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private String aggregationType;
    private Integer observationWindowHours;
    private Integer minimumObservationCount;
    private String triggerDescription;
    private Long updatedBy;
}
