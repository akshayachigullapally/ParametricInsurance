package org.example.triggerservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.junit.jupiter.api.Test;

class DemoSimulationServiceTest{
    private final DemoSimulationService service=new DemoSimulationService(
            mock(TriggerDefinitionRepository.class),
            mock(ObservationRepository.class),
            mock(LocationZoneRepository.class),
            mock(TriggerEvaluationService.class));

    @Test
    void sumSpreadsTheTargetAcrossReadings(){
        // 150 × 1.10 ÷ 24 = 6.875 per reading, 24 readings sum to 165 ≥ 150
        assertThat(service.valuePerReading(definition(AggregationType.SUM,ComparisonOperator.GREATER_THAN_OR_EQUAL),24))
                .isEqualByComparingTo("6.875");
    }

    @Test
    void averageKeepsEveryReadingAboveTheThreshold(){
        // Every reading is 165, so the average is 165 ≥ 150 whatever the count
        assertThat(service.valuePerReading(definition(AggregationType.AVERAGE,ComparisonOperator.GREATER_THAN_OR_EQUAL),24))
                .isEqualByComparingTo("165");
    }

    @Test
    void averageBelowModeKeepsEveryReadingUnderTheThreshold(){
        assertThat(service.valuePerReading(definition(AggregationType.AVERAGE,ComparisonOperator.LESS_THAN),24))
                .isEqualByComparingTo("135");
    }

    @Test
    void maximumUsesTheTargetDirectly(){
        assertThat(service.valuePerReading(definition(AggregationType.MAXIMUM,ComparisonOperator.GREATER_THAN_OR_EQUAL),6))
                .isEqualByComparingTo("165");
    }

    private TriggerDefinition definition(AggregationType aggregation,ComparisonOperator operator){
        return TriggerDefinition.builder()
                .thresholdValue(new BigDecimal("150"))
                .aggregationType(aggregation)
                .comparisonOperator(operator)
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .build();
    }
}
