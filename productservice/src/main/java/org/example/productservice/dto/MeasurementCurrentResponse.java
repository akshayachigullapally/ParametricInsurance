package org.example.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementCurrentResponse{
    private String measurementType;
    private String measurementUnit;
    private BigDecimal currentValue;
    private LocalDateTime observedAt;
    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;
    private String status;
}