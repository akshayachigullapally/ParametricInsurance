package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.dto.DemoSimulationResult;
import org.example.triggerservice.dto.EvaluateTriggerRequest;
import org.example.triggerservice.dto.TriggerEvaluationResponse;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoSimulationService{
    private static final BigDecimal CROSS_MARGIN=new BigDecimal("1.10");
    private static final BigDecimal BELOW_MARGIN=new BigDecimal("0.90");

    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;
    private final TriggerEvaluationService triggerEvaluationService;

    public Flux<DemoSimulationResult> simulateThresholdCrossing(
            Long productId,
            Long userId
    ){
        if(productId==null||productId<=0){
            throw new ValidationException(
                    "Product ID must be greater than zero");
        }

        return triggerDefinitionRepository
                .findAllByProductIdAndStatusNotOrderByDefinitionNameAsc(
                        productId,
                        TriggerDefinitionStatus.ARCHIVED)
                .filter(definition->
                        definition.getStatus()==TriggerDefinitionStatus.ACTIVE)
                .flatMap(definition->injectCrossingObservations(
                                definition,
                                userId)
                        .flatMap(count->locationZoneRepository
                                .findById(definition.getLocationZoneId())
                                .defaultIfEmpty(LocationZone.builder()
                                        .id(definition.getLocationZoneId())
                                        .zoneCode("UNAVAILABLE")
                                        .build())
                                .flatMap(zone->triggerEvaluationService
                                        .evaluate(
                                                definition.getId(),
                                                new EvaluateTriggerRequest(
                                                        LocalDateTime.now()),
                                                userId)
                                        .map(evaluation->buildResult(
                                                definition,
                                                zone,
                                                count,
                                                evaluation))))
                        .onErrorResume(error->{
                            log.warn("Demo simulation failed for definition {}: {}",
                                    definition.getDefinitionCode(),
                                    error.getMessage());
                            return Mono.empty();
                        }));
    }

    private Mono<Integer> injectCrossingObservations(
            TriggerDefinition definition,
            Long userId
    ){
        int count=Math.max(
                Math.max(definition.getObservationWindowHours(),
                        definition.getMinimumObservationCount()),
                1);

        BigDecimal perValue=valuePerReading(definition,count);

        boolean lessMode=isLessMode(definition.getComparisonOperator());

        return Flux.range(0,count)
                .flatMap(index->saveCrossingReading(
                        definition,
                        userId,
                        index,
                        count,
                        perValue,
                        lessMode))
                .collectList()
                .map(list->list.size());
    }

    private Mono<Observation> saveCrossingReading(
            TriggerDefinition definition,
            Long userId,
            int index,
            int count,
            BigDecimal value,
            boolean lessMode
    ){
        long windowMinutes=(long)definition.getObservationWindowHours()*60;
        long minutesBack=Math.max(
                (windowMinutes*(index+1))/Math.max(count,1),
                1L);

        Observation observation=Observation.builder()
                .observationReference(generateReference())
                .locationZoneId(definition.getLocationZoneId())
                .measurementType(definition.getMeasurementType())
                .measurementUnit(definition.getMeasurementUnit())
                .observedValue(value)
                .observedAt(LocalDateTime.now().minusMinutes(minutesBack))
                .source(ObservationSource.MOCK)
                .sourceReference("MOCK-SIM-"+UUID.randomUUID()
                        .toString().replace("-","")
                        .substring(0,12).toUpperCase(Locale.ROOT))
                .status(ObservationStatus.VALIDATED)
                .receivedBy(userId)
                .receivedAt(LocalDateTime.now())
                .build();

        return observationRepository.save(observation);
    }

    BigDecimal valuePerReading(
            TriggerDefinition definition,
            int count
    ){
        boolean lessMode=isLessMode(definition.getComparisonOperator());
        BigDecimal target=definition.getThresholdValue()
                .multiply(lessMode?BELOW_MARGIN:CROSS_MARGIN);

        // Only a SUM spreads the target across readings; AVERAGE, MIN, MAX and
        // LATEST all need every reading to sit at the target itself.
        if(definition.getAggregationType()==AggregationType.SUM){
            return target.divide(BigDecimal.valueOf(count),4,RoundingMode.HALF_UP);
        }

        return target;
    }

    private boolean isLessMode(ComparisonOperator operator){
        return operator==ComparisonOperator.LESS_THAN||
                operator==ComparisonOperator.LESS_THAN_OR_EQUAL;
    }

    private DemoSimulationResult buildResult(
            TriggerDefinition definition,
            LocationZone zone,
            int observationsInjected,
            TriggerEvaluationResponse evaluation
    ){
        return DemoSimulationResult.builder()
                .triggerDefinitionId(definition.getId())
                .definitionCode(definition.getDefinitionCode())
                .locationZoneId(zone.getId())
                .zoneCode(zone.getZoneCode())
                .observationsInjected(observationsInjected)
                .evaluation(evaluation)
                .build();
    }

    private String generateReference(){
        return "OBS-"+LocalDateTime.now().getYear()+"-"+UUID.randomUUID()
                .toString().replace("-","")
                .substring(0,10).toUpperCase(Locale.ROOT);
    }
}