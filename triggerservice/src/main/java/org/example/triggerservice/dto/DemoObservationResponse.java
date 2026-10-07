package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoObservationResponse {
    private Long observationId;
    private String observationReference;
    private String observationStatus;
    private Long evaluationId;
    private String evaluationReference;
    private String evaluationResult;
    private BigDecimal aggregatedValue;
    private BigDecimal thresholdValue;
    private String comparisonOperator;
    private Integer observationCount;
    private Long triggerEventId;
    private String triggerEventReference;
    private String triggerEventStatus;
    private List<String> eligiblePolicyNumbers;
    private Integer claimsCreated;
    private String message;
}