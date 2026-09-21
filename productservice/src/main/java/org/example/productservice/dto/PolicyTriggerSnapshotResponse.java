package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyTriggerSnapshotResponse{
    private Long id;
    private Long policyId;
    private String perilCode;
    private String triggerMetric;
    private String triggerOperator;
    private BigDecimal triggerThreshold;
    private Integer triggerDurationHours;
    private String triggerAggregationType;
    private Integer triggerMinObservationCount;
    private Long productId;
    private String productCode;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private String aggregationType;
    private Integer observationWindowHours;
    private Integer minimumObservationCount;
    private String triggerDescription;
    private String humanReadableTrigger;
    private LocalDateTime capturedAt;
    private LocalDateTime createdAt;
}
