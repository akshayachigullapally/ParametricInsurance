package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationResponse{
    private Long id;
    private String observationReference;

    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;

    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private BigDecimal observedValue;
    private LocalDateTime observedAt;

    private ObservationSource source;
    private String sourceReference;
    private ObservationStatus status;

    private Long receivedBy;
    private LocalDateTime receivedAt;
}