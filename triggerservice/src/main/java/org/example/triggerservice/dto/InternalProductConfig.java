package org.example.triggerservice.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalProductConfig{
    private Long productId;
    private String productCode;
    private String productName;
    private String status;
    private String perilCode;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private Integer observationWindowHours;
    private String triggerDescription;
}
