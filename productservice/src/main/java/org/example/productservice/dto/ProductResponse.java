package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse{
    private Long id;
    private String productCode;
    private String name;
    private String description;
    private String category;
    private String status;
    private BigDecimal minimumSumInsured;
    private BigDecimal maximumSumInsured;
    private Integer minimumTermMonths;
    private Integer maximumTermMonths;
    private String eligibilityDescription;
    private String perilCode;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private String aggregationType;
    private Integer observationWindowHours;
    private Integer minimumObservationCount;
    private String triggerDescription;
    private String humanReadableTrigger;
    private String triggerSyncStatus;
    private java.time.LocalDateTime triggerSyncedAt;
    private String triggerSyncError;
    private Boolean customerDefined;
    private Long createdBy;
    private List<CoverageResponse> coverages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}