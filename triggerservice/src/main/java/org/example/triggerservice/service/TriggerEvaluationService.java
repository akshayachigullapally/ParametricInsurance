package org.example.triggerservice.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.triggerservice.dto.EvaluateTriggerRequest;
import org.example.triggerservice.dto.TriggerEvaluationResponse;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.AggregationType;
import org.example.triggerservice.model.ComparisonOperator;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.model.TriggerEvaluation;
import org.example.triggerservice.model.TriggerEvaluationResult;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEvaluationRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerEvaluationService{
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final TriggerEvaluationRepository triggerEvaluationRepository;
    private final TriggerEventRepository triggerEventRepository;
    private final ObservationRepository observationRepository;
    private final LocationZoneRepository locationZoneRepository;

    public Flux<TriggerEvaluationResponse> getAll(){
        log.debug("Get all [trigger evaluation]");
        return triggerEvaluationRepository.findAllByOrderByEvaluatedAtDesc()
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEvaluationResponse> getById(Long evaluationId){
        log.debug("Get by id [trigger evaluation]: evaluationId={}",evaluationId);
        validateId(evaluationId,"Trigger evaluation ID");

        return triggerEvaluationRepository.findById(evaluationId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger evaluation not found")))
                .flatMap(this::buildResponse);
    }

    public Flux<TriggerEvaluationResponse> getByDefinitionId(
            Long definitionId
    ){
        log.debug("Get by definition id [trigger evaluation]: definitionId={}",definitionId);
        validateId(definitionId,"Trigger definition ID");

        return triggerDefinitionRepository.findById(definitionId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger definition not found")))
                .thenMany(triggerEvaluationRepository
                        .findAllByTriggerDefinitionIdOrderByEvaluatedAtDesc(
                                definitionId))
                .flatMap(this::buildResponse);
    }

    public Mono<TriggerEvaluationResponse> evaluate(
            Long definitionId,
            EvaluateTriggerRequest request,
            Long userId
    ){
        log.info("Evaluate [trigger evaluation]: definitionId={}, userId={}",definitionId,userId);
        validateId(definitionId,"Trigger definition ID");

        LocalDateTime windowEnd=request!=null&&
                request.getEvaluationEnd()!=null
                ?request.getEvaluationEnd()
                :LocalDateTime.now();

        if(windowEnd.isAfter(LocalDateTime.now())){
            return Mono.error(new ValidationException(
                    "Evaluation end time cannot be in the future"));
        }

        return triggerDefinitionRepository.findById(definitionId)
                .switchIfEmpty(Mono.error(
                        new NotFoundException("Trigger definition not found")))
                .flatMap(definition->validateDefinition(definition)
                        .then(evaluateDefinition(
                                definition,
                                windowEnd,
                                userId)));
    }

    private Mono<TriggerEvaluationResponse> evaluateDefinition(
            TriggerDefinition definition,
            LocalDateTime windowEnd,
            Long userId
    ){
        LocalDateTime windowStart=windowEnd.minusHours(
                definition.getObservationWindowHours());

        log.debug("Evaluating trigger definition {} for window {} - {}",
                definition.getId(),windowStart,windowEnd);

        return observationRepository
                .findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
                        definition.getLocationZoneId(),
                        definition.getMeasurementType(),
                        ObservationStatus.VALIDATED,
                        windowStart,
                        windowEnd)
                .filter(observation->
                        observation.getMeasurementUnit()==
                                definition.getMeasurementUnit())
                .collectList()
                .flatMap(observations->saveEvaluation(
                        definition,
                        observations,
                        windowStart,
                        windowEnd,
                        userId));
    }

    private Mono<TriggerEvaluationResponse> saveEvaluation(
            TriggerDefinition definition,
            List<Observation> observations,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            Long userId
    ){
        int observationCount=observations.size();

        if(observationCount<definition.getMinimumObservationCount()){
            log.info("Trigger definition {}: insufficient observations ({}/{})",
                    definition.getId(),observationCount,
                    definition.getMinimumObservationCount());
            return saveEvaluationRecord(
                    definition,
                    windowStart,
                    windowEnd,
                    observationCount,
                    null,
                    TriggerEvaluationResult.INSUFFICIENT_OBSERVATIONS,
                    userId
            ).flatMap(this::buildResponse);
        }

        BigDecimal aggregatedValue=aggregate(
                observations,
                definition.getAggregationType());

        boolean matched=compare(
                aggregatedValue,
                definition.getThresholdValue(),
                definition.getComparisonOperator());

        TriggerEvaluationResult result=matched
                ?TriggerEvaluationResult.MATCHED
                :TriggerEvaluationResult.NOT_MATCHED;

        log.info("Trigger definition {} evaluated: {} {} {} -> {}",
                definition.getId(),aggregatedValue,
                definition.getComparisonOperator(),
                definition.getThresholdValue(),result);

        return saveEvaluationRecord(
                definition,
                windowStart,
                windowEnd,
                observationCount,
                aggregatedValue,
                result,
                userId
        )
                .flatMap(evaluation->{
                    if(result!=TriggerEvaluationResult.MATCHED){
                        return buildResponse(evaluation);
                    }

                    return createEventIfRequired(definition,evaluation)
                            .then(buildResponse(evaluation));
                });
    }

    private Mono<TriggerEvaluation> saveEvaluationRecord(
            TriggerDefinition definition,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            Integer observationCount,
            BigDecimal aggregatedValue,
            TriggerEvaluationResult result,
            Long userId
    ){
        TriggerEvaluation evaluation=TriggerEvaluation.builder()
                .evaluationReference(generateEvaluationReference())
                .triggerDefinitionId(definition.getId())
                .locationZoneId(definition.getLocationZoneId())
                .measurementType(definition.getMeasurementType())
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .observationCount(observationCount)
                .aggregatedValue(aggregatedValue)
                .thresholdValue(definition.getThresholdValue())
                .comparisonOperator(definition.getComparisonOperator())
                .result(result)
                .evaluatedBy(userId)
                .evaluatedAt(LocalDateTime.now())
                .build();

        return triggerEvaluationRepository.save(evaluation);
    }

    private Mono<TriggerEvent> createEventIfRequired(
            TriggerDefinition definition,
            TriggerEvaluation evaluation
    ){
        return triggerEventRepository
                .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                        definition.getId(),
                        evaluation.getWindowStart(),
                        evaluation.getWindowEnd(),
                        TriggerEventStatus.REJECTED)
                .flatMap(exists->{
                    if(exists){
                        log.debug("Trigger event already exists for definition {} in this window",
                                definition.getId());
                        return Mono.empty();
                    }

                    TriggerEvent event=TriggerEvent.builder()
                            .eventReference(generateEventReference())
                            .triggerDefinitionId(definition.getId())
                            .triggerEvaluationId(evaluation.getId())
                            .perilId(definition.getPerilId())
                            .locationZoneId(definition.getLocationZoneId())
                            .productId(definition.getProductId())
                            .measurementType(definition.getMeasurementType())
                            .measurementUnit(definition.getMeasurementUnit())
                            .aggregatedValue(evaluation.getAggregatedValue())
                            .thresholdValue(definition.getThresholdValue())
                            .windowStart(evaluation.getWindowStart())
                            .windowEnd(evaluation.getWindowEnd())
                            .status(TriggerEventStatus.DETECTED)
                            .detectedAt(LocalDateTime.now())
                            .build();

                    log.info("Trigger event {} DETECTED for definition {} zone {}",
                            event.getEventReference(),definition.getId(),
                            definition.getLocationZoneId());

                    return triggerEventRepository.save(event);
                });
    }

    private Mono<Void> validateDefinition(TriggerDefinition definition){
        if(definition.getStatus()!=TriggerDefinitionStatus.ACTIVE){
            return Mono.error(new ConflictException(
                    "Only ACTIVE trigger definitions can be evaluated"));
        }

        LocalDate today=LocalDate.now();

        if(definition.getEffectiveFrom().isAfter(today)){
            return Mono.error(new ConflictException(
                    "Trigger definition is not yet effective"));
        }

        if(definition.getEffectiveTo()!=null&&
                definition.getEffectiveTo().isBefore(today)){
            return Mono.error(new ConflictException(
                    "Trigger definition has expired"));
        }

        return Mono.empty();
    }

    private BigDecimal aggregate(
            List<Observation> observations,
            AggregationType aggregationType
    ){
        return switch(aggregationType){
            case SUM->observations.stream()
                    .map(Observation::getObservedValue)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);

            case AVERAGE->observations.stream()
                    .map(Observation::getObservedValue)
                    .reduce(BigDecimal.ZERO,BigDecimal::add)
                    .divide(
                            BigDecimal.valueOf(observations.size()),
                            4,
                            RoundingMode.HALF_UP);

            case MINIMUM->observations.stream()
                    .map(Observation::getObservedValue)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            case MAXIMUM->observations.stream()
                    .map(Observation::getObservedValue)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            case LATEST->observations.stream()
                    .max(Comparator.comparing(Observation::getObservedAt))
                    .map(Observation::getObservedValue)
                    .orElse(BigDecimal.ZERO);
        };
    }

    private boolean compare(
            BigDecimal aggregatedValue,
            BigDecimal thresholdValue,
            ComparisonOperator operator
    ){
        int result=aggregatedValue.compareTo(thresholdValue);

        return switch(operator){
            case GREATER_THAN->result>0;
            case GREATER_THAN_OR_EQUAL->result>=0;
            case LESS_THAN->result<0;
            case LESS_THAN_OR_EQUAL->result<=0;
            case EQUAL->result==0;
        };
    }

    private Mono<TriggerEvaluationResponse> buildResponse(
            TriggerEvaluation evaluation
    ){
        Mono<TriggerDefinition> definitionMono=
                triggerDefinitionRepository
                        .findById(evaluation.getTriggerDefinitionId())
                        .defaultIfEmpty(TriggerDefinition.builder()
                                .id(evaluation.getTriggerDefinitionId())
                                .definitionCode("UNAVAILABLE")
                                .build());

        Mono<LocationZone> zoneMono=locationZoneRepository
                .findById(evaluation.getLocationZoneId())
                .defaultIfEmpty(LocationZone.builder()
                        .id(evaluation.getLocationZoneId())
                        .zoneCode("UNAVAILABLE")
                        .build());

        Mono<TriggerEvent> eventMono=triggerEventRepository
                .findByTriggerEvaluationId(evaluation.getId())
                .defaultIfEmpty(TriggerEvent.builder().build());

        return Mono.zip(definitionMono,zoneMono,eventMono)
                .map(tuple->TriggerEvaluationResponse.builder()
                        .id(evaluation.getId())
                        .evaluationReference(
                                evaluation.getEvaluationReference())
                        .triggerDefinitionId(
                                evaluation.getTriggerDefinitionId())
                        .definitionCode(
                                tuple.getT1().getDefinitionCode())
                        .locationZoneId(evaluation.getLocationZoneId())
                        .zoneCode(tuple.getT2().getZoneCode())
                        .measurementType(evaluation.getMeasurementType())
                        .windowStart(evaluation.getWindowStart())
                        .windowEnd(evaluation.getWindowEnd())
                        .observationCount(evaluation.getObservationCount())
                        .aggregatedValue(evaluation.getAggregatedValue())
                        .thresholdValue(evaluation.getThresholdValue())
                        .comparisonOperator(
                                evaluation.getComparisonOperator())
                        .result(evaluation.getResult())
                        .triggerEventId(tuple.getT3().getId())
                        .triggerEventReference(
                                tuple.getT3().getEventReference())
                        .evaluatedBy(evaluation.getEvaluatedBy())
                        .evaluatedAt(evaluation.getEvaluatedAt())
                        .build());
    }

    private String generateEvaluationReference(){
        return "EVAL-"+LocalDateTime.now().getYear()+"-"+
                randomReference();
    }

    private String generateEventReference(){
        return "TEV-"+LocalDateTime.now().getYear()+"-"+
                randomReference();
    }

    private String randomReference(){
        return UUID.randomUUID()
                .toString()
                .replace("-","")
                .substring(0,10)
                .toUpperCase(Locale.ROOT);
    }

    private void validateId(Long id,String fieldName){
        if(id==null||id<=0){
            throw new ValidationException(
                    fieldName+" must be greater than zero");
        }
    }
}