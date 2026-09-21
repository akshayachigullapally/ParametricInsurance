package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.TriggerDefinitionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerDefinitionResponse{
    private Long id;
    private String definitionCode;
    private String definitionName;
    private String description;

    private Long perilId;
    private String perilCode;
    private String perilName;

    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;

    private Long productId;

    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private AggregationType aggregationType;
    private ComparisonOperator comparisonOperator;

    private BigDecimal thresholdValue;
    private Integer observationWindowHours;
    private Integer minimumObservationCount;

    private TriggerDefinitionStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}