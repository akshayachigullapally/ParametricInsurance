package org.example.triggerservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.triggerservice.model.MonitorRun;
import org.example.triggerservice.model.MonitorRunStatus;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.example.triggerservice.model.TriggerEvaluation;
import org.example.triggerservice.model.TriggerEvaluationResult;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.example.triggerservice.repository.MonitorCheckpointRepository;
import org.example.triggerservice.repository.MonitorRunRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEvaluationRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorRunService{
    private final MonitorRunRepository monitorRunRepository;
    private final MonitorCheckpointRepository monitorCheckpointRepository;
    private final TriggerDefinitionRepository triggerDefinitionRepository;
    private final ObservationRepository observationRepository;
    private final TriggerEvaluationRepository triggerEvaluationRepository;
    private final TriggerEventRepository triggerEventRepository;
    private final IdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${monitor.scan.interval:60000}")
    public Mono<Void> runScheduledScan(){
        return monitorRunRepository.findTopByStatusOrderByStartedAtDesc(MonitorRunStatus.RUNNING)
                .flatMap(running->{
                    log.warn("Skipping scheduled scan: previous run still in progress (id={})", running.getId());
                    return Mono.<Void>empty();
                })
                .switchIfEmpty(Mono.defer(this::executeScan).then());
    }

    public Mono<MonitorRun> triggerManualScan(){
        return monitorRunRepository.findTopByStatusOrderByStartedAtDesc(MonitorRunStatus.RUNNING)
                .flatMap(running->{
                    log.warn("Manual scan rejected: a scan is already in progress (id={})", running.getId());
                    return Mono.<MonitorRun>error(
                            new org.example.triggerservice.exception.ConflictException(
                                    "A monitor scan is already in progress"));
                })
                .switchIfEmpty(Mono.defer(this::executeScan));
    }

    public Flux<MonitorRun> getAllRuns(){
        log.debug("Get all runs [monitor run]");
        return monitorRunRepository.findAllByOrderByStartedAtDesc();
    }

    public Mono<MonitorRun> getRunById(Long runId){
        log.debug("Get run by id [monitor run]: runId={}",runId);
        return monitorRunRepository.findById(runId)
                .switchIfEmpty(Mono.error(
                        new org.example.triggerservice.exception.NotFoundException(
                                "Monitor run not found")));
    }

    private Mono<MonitorRun> executeScan(){
        LocalDateTime now=LocalDateTime.now();

        MonitorRun run=MonitorRun.builder()
                .startedAt(now)
                .status(MonitorRunStatus.RUNNING)
                .observationsProcessed(0)
                .eventsCreated(0)
                .build();

        return monitorRunRepository.save(run)
                .flatMap(savedRun->
                        triggerDefinitionRepository
                                .findAllByStatusOrderByDefinitionNameAsc(TriggerDefinitionStatus.ACTIVE)
                                .collectList()
                                .flatMap(definitions->processDefinitions(savedRun, definitions))
                );
    }

    private Mono<MonitorRun> processDefinitions(
            MonitorRun run,
            List<TriggerDefinition> definitions
    ){
        if(definitions.isEmpty()){
            return completeRun(run, 0, 0, null);
        }

        return Flux.fromIterable(definitions)
                .flatMap(definition->evaluateDefinition(definition, run.getId()))
                .collectList()
                .flatMap(results->{
                    int observationsProcessed=results.stream()
                            .mapToInt(r->r.observationCount)
                            .sum();

                    long eventsCreated=results.stream()
                            .filter(r->r.eventCreated)
                            .count();

                    return completeRun(
                            run,
                            observationsProcessed,
                            (int)eventsCreated,
                            null);
                })
                .onErrorResume(error->{
                    log.error("Monitor scan failed: {}", error.getMessage(), error);
                    return completeRun(run, 0, 0, error.getMessage());
                });
    }

    private Mono<EvaluationResult> evaluateDefinition(
            TriggerDefinition definition,
            Long monitorRunId
    ){
        LocalDateTime now=LocalDateTime.now();
        LocalDateTime windowEnd=now;
        LocalDateTime windowStart=now.minusHours(definition.getObservationWindowHours());

        return observationRepository
                .findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
                        definition.getLocationZoneId(),
                        definition.getMeasurementType(),
                        ObservationStatus.VALIDATED,
                        windowStart,
                        windowEnd)
                .filter(observation->
                        observation.getMeasurementUnit()==definition.getMeasurementUnit())
                .collectList()
                .flatMap(observations->{
                    int observationCount=observations.size();

                    if(observationCount<definition.getMinimumObservationCount()){
                        return saveEvaluationRecord(
                                definition, windowStart, windowEnd,
                                observationCount, null,
                                TriggerEvaluationResult.INSUFFICIENT_OBSERVATIONS,
                                monitorRunId)
                                .map(eval->new EvaluationResult(observationCount, false));
                    }

                    BigDecimal aggregatedValue=aggregate(observations, definition.getAggregationType());
                    boolean matched=compare(aggregatedValue, definition.getThresholdValue(), definition.getComparisonOperator());

                    TriggerEvaluationResult result=matched
                            ?TriggerEvaluationResult.MATCHED
                            :TriggerEvaluationResult.NOT_MATCHED;

                    return saveEvaluationRecord(
                            definition, windowStart, windowEnd,
                            observationCount, aggregatedValue, result, monitorRunId)
                            .flatMap(evaluation->{
                                if(!matched){
                                    return Mono.just(new EvaluationResult(observationCount, false));
                                }

                                return createEventIfRequired(definition, evaluation)
                                        .map(eventCreated->
                                                new EvaluationResult(observationCount, eventCreated));
                            });
                });
    }

    private Mono<TriggerEvaluation> saveEvaluationRecord(
            TriggerDefinition definition,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            Integer observationCount,
            BigDecimal aggregatedValue,
            TriggerEvaluationResult result,
            Long monitorRunId
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
                .evaluatedBy(monitorRunId)
                .evaluatedAt(LocalDateTime.now())
                .build();

        return triggerEvaluationRepository.save(evaluation);
    }

    private Mono<Boolean> createEventIfRequired(
            TriggerDefinition definition,
            TriggerEvaluation evaluation
    ){
        String idempotencyKey=IdempotencyService.buildTriggerEventKey(
                definition.getId(),
                evaluation.getWindowStart(),
                evaluation.getWindowEnd());

        return idempotencyService.isDuplicate(idempotencyKey)
                .flatMap(isDuplicate->{
                    if(isDuplicate){
                        log.debug("Skipping duplicate trigger event for definition {} window {}-{}",
                                definition.getId(), evaluation.getWindowStart(), evaluation.getWindowEnd());
                        return Mono.just(false);
                    }

                    return triggerEventRepository
                            .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                                    definition.getId(),
                                    evaluation.getWindowStart(),
                                    evaluation.getWindowEnd(),
                                    TriggerEventStatus.REJECTED)
                            .flatMap(exists->{
                                if(exists){
                                    return Mono.just(false);
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

                                return triggerEventRepository.save(event)
                                        .flatMap(savedEvent->
                                                idempotencyService.record(
                                                        idempotencyKey,
                                                        "TRIGGER_EVENT",
                                                        savedEvent.getId())
                                                        .map(r->true));
                            });
                });
    }

    private BigDecimal aggregate(
            List<Observation> observations,
            org.example.triggerservice.model.AggregationType aggregationType
    ){
        return switch(aggregationType){
            case SUM->observations.stream()
                    .map(Observation::getObservedValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            case AVERAGE->observations.stream()
                    .map(Observation::getObservedValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(
                            BigDecimal.valueOf(observations.size()),
                            4, RoundingMode.HALF_UP);

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
            org.example.triggerservice.model.ComparisonOperator operator
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

    private Mono<MonitorRun> completeRun(
            MonitorRun run,
            int observationsProcessed,
            int eventsCreated,
            String failureMessage
    ){
        run.setCompletedAt(LocalDateTime.now());
        run.setObservationsProcessed(observationsProcessed);
        run.setEventsCreated(eventsCreated);

        if(failureMessage!=null){
            run.setStatus(MonitorRunStatus.FAILED);
            run.setFailureMessage(failureMessage);
        }else{
            run.setStatus(MonitorRunStatus.COMPLETED);
        }

        return monitorRunRepository.save(run)
                .doOnNext(r->log.info(
                        "Monitor scan completed: id={}, status={}, observations={}, events={}",
                        r.getId(), r.getStatus(), r.getObservationsProcessed(), r.getEventsCreated()));
    }

    private String generateEvaluationReference(){
        return "EVAL-"+LocalDateTime.now().getYear()+"-"+randomReference();
    }

    private String generateEventReference(){
        return "TEV-"+LocalDateTime.now().getYear()+"-"+randomReference();
    }

    private String randomReference(){
        return UUID.randomUUID()
                .toString().replace("-","")
                .substring(0,10)
                .toUpperCase(Locale.ROOT);
    }

    private record EvaluationResult(int observationCount, boolean eventCreated){}
}
