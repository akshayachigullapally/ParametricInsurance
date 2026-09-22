package org.example.productservice.dto;

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
public class InternalPolicyResponse {
    private Long id;
    private String policyNumber;
    private Long customerId;
    private Long productId;
    private String productCode;
    private String productName;
    private Long locationZoneId;
    private BigDecimal sumInsured;
    private BigDecimal premiumAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String snapshotMeasurementType;
    private String snapshotMeasurementUnit;
    private BigDecimal snapshotThresholdValue;
    private String snapshotComparisonOperator;
    private String snapshotAggregationType;
    private Integer snapshotObservationWindowHours;
    private Integer snapshotMinimumObservationCount;
}