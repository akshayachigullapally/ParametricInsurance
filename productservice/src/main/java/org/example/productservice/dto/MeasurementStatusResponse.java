package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementStatusResponse{
    private String measurementType;
    private String displayName;
    private String measurementUnit;
    private BigDecimal currentValue;
    private LocalDateTime observedAt;
    private String zoneCode;
    private String zoneName;
    private List<ProductTriggerStatus> products;
}