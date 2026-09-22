package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.ObservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementCurrentResponse{
    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private BigDecimal currentValue;
    private LocalDateTime observedAt;
    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;
    private ObservationStatus status;
}