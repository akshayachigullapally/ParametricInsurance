package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalProductResponse {
    private Long productId;
    private String productCode;
    private String productName;
    private String category;
    private String status;
    private String perilCode;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private String aggregationType;
    private Integer observationWindowHours;
    private Integer minimumObservationCount;
    private String triggerDescription;
    private BigDecimal minSumInsured;
    private BigDecimal maxSumInsured;
    private Integer minTermMonths;
    private Integer maxTermMonths;
    private Boolean onlineSale;
}