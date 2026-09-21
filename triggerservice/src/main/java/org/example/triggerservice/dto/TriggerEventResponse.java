package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.TriggerEventStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerEventResponse{
    private Long id;
    private String eventReference;

    private Long triggerDefinitionId;
    private String definitionCode;
    private String definitionName;

    private Long triggerEvaluationId;

    private Long perilId;
    private String perilCode;
    private String perilName;

    private Long locationZoneId;
    private String zoneCode;
    private String zoneName;

    private Long productId;
    private MeasurementType measurementType;
    private MeasurementUnit measurementUnit;
    private BigDecimal aggregatedValue;
    private BigDecimal thresholdValue;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    private TriggerEventStatus status;
    private LocalDateTime detectedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
}