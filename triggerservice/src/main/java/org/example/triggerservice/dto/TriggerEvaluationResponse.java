package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.TriggerEvaluationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerEvaluationResponse{
    private Long id;
    private String evaluationReference;
    private Long triggerDefinitionId;
    private String definitionCode;
    private Long locationZoneId;
    private String zoneCode;
    private MeasurementType measurementType;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private Integer observationCount;
    private BigDecimal aggregatedValue;
    private BigDecimal thresholdValue;
    private ComparisonOperator comparisonOperator;
    private TriggerEvaluationResult result;
    private Long triggerEventId;
    private String triggerEventReference;
    private Long evaluatedBy;
    private LocalDateTime evaluatedAt;
}