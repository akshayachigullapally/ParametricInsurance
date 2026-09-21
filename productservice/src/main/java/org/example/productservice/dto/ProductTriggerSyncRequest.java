package org.example.productservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTriggerSyncRequest{
    private Long productId;
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