package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductThresholdHistoryResponse{
    private Long id;
    private Long productId;
    private String productCode;
    private String previousPerilCode;
    private String newPerilCode;
    private String previousMeasurementType;
    private String newMeasurementType;
    private String previousMeasurementUnit;
    private String newMeasurementUnit;
    private BigDecimal previousThresholdValue;
    private BigDecimal newThresholdValue;
    private String previousComparisonOperator;
    private String newComparisonOperator;
    private String previousAggregationType;
    private String newAggregationType;
    private Integer previousObservationWindowHours;
    private Integer newObservationWindowHours;
    private Integer previousMinimumObservationCount;
    private Integer newMinimumObservationCount;
    private Long changedBy;
    private String changeReason;
    private LocalDateTime changedAt;
    private String triggerSyncStatus;
}