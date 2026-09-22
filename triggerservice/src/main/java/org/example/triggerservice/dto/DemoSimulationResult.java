package org.example.triggerservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSimulationResult{
    private Long triggerDefinitionId;
    private String definitionCode;
    private Long locationZoneId;
    private String zoneCode;
    private int observationsInjected;
    private TriggerEvaluationResponse evaluation;
}