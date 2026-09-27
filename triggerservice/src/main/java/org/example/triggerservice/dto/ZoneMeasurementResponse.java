package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Latest validated reading of one measurement type in one zone. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneMeasurementResponse{
    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;
    private String measurementType;
    private String measurementUnit;
    private BigDecimal currentValue;
    private LocalDateTime observedAt;
}
