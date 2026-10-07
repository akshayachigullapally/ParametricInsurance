package org.example.triggerservice.service;

import org.example.triggerservice.dto.EvaluateTriggerRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.*;
import org.example.triggerservice.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerEvaluationServiceTest {
    @Mock private TriggerDefinitionRepository triggerDefinitionRepository;
    @Mock private TriggerEvaluationRepository triggerEvaluationRepository;
    @Mock private TriggerEventRepository triggerEventRepository;
    @Mock private ObservationRepository observationRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @InjectMocks private TriggerEvaluationService service;

    @Test
    void getAll_shouldReturnEnrichedEvaluations() {
        when(triggerEvaluationRepository.findAllByOrderByEvaluatedAtDesc())
                .thenReturn(Flux.just(evaluation(TriggerEvaluationResult.MATCHED)));
        mockEvaluationResponse();

        StepVerifier.create(service.getAll())
                .assertNext(result->{
                    assertThat(result.getEvaluationReference()).isEqualTo("EVAL-001");
                    assertThat(result.getDefinitionCode()).isEqualTo("DEF-001");
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                })
                .verifyComplete();
    }

    @Test
    void getById_whenEvaluationExists_shouldReturnEvaluation() {
        when(triggerEvaluationRepository.findById(1L))
                .thenReturn(Mono.just(evaluation(TriggerEvaluationResult.MATCHED)));
        mockEvaluationResponse();

        StepVerifier.create(service.getById(1L))
                .assertNext(result->assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getByDefinitionId_shouldReturnEvaluations() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(triggerEvaluationRepository
                .findAllByTriggerDefinitionIdOrderByEvaluatedAtDesc(1L))
                .thenReturn(Flux.just(evaluation(TriggerEvaluationResult.MATCHED)));
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone()));
        when(triggerEventRepository.findByTriggerEvaluationId(1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.getByDefinitionId(1L))
                .assertNext(result->assertThat(result.getTriggerDefinitionId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void evaluate_whenThresholdMatches_shouldSaveEvaluationAndEvent() {
        mockEvaluateCommon(Flux.just(observation(new BigDecimal("160.00"))));
        when(triggerEventRepository
                .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                        eq(1L),any(LocalDateTime.class),any(LocalDateTime.class),
                        eq(TriggerEventStatus.REJECTED)))
                .thenReturn(Mono.just(false));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->{
                    TriggerEvent saved=invocation.getArgument(0);
                    saved.setId(10L);
                    return Mono.just(saved);
                });

        StepVerifier.create(service.evaluate(1L,null,5L))
                .assertNext(result->{
                    assertThat(result.getResult()).isEqualTo(TriggerEvaluationResult.MATCHED);
                    assertThat(result.getAggregatedValue())
                            .isEqualByComparingTo("160.00");
                })
                .verifyComplete();

        verify(triggerEventRepository).save(any(TriggerEvent.class));
    }

    @Test
    void evaluate_whenThresholdDoesNotMatch_shouldNotCreateEvent() {
        mockEvaluateCommon(Flux.just(observation(new BigDecimal("100.00"))));

        StepVerifier.create(service.evaluate(1L,null,5L))
                .assertNext(result->assertThat(result.getResult())
                        .isEqualTo(TriggerEvaluationResult.NOT_MATCHED))
                .verifyComplete();

        verify(triggerEventRepository,never())
                .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                        any(),any(),any(),any());
        verify(triggerEventRepository,never()).save(any());
    }

    @Test
    void evaluate_whenObservationsAreInsufficient_shouldSaveInsufficientResult() {
        mockEvaluateCommon(Flux.empty());

        StepVerifier.create(service.evaluate(1L,null,5L))
                .assertNext(result->{
                    assertThat(result.getResult())
                            .isEqualTo(TriggerEvaluationResult.INSUFFICIENT_OBSERVATIONS);
                    assertThat(result.getObservationCount()).isZero();
                    assertThat(result.getAggregatedValue()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void evaluate_whenEventAlreadyExists_shouldNotCreateAnotherEvent() {
        mockEvaluateCommon(Flux.just(observation(new BigDecimal("160.00"))));
        when(triggerEventRepository
                .existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
                        eq(1L),any(LocalDateTime.class),any(LocalDateTime.class),
                        eq(TriggerEventStatus.REJECTED)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.evaluate(1L,null,5L))
                .assertNext(result->assertThat(result.getResult())
                        .isEqualTo(TriggerEvaluationResult.MATCHED))
                .verifyComplete();

        verify(triggerEventRepository,never()).save(any());
    }

    @Test
    void evaluate_whenDefinitionIsInactive_shouldReturnConflict() {
        TriggerDefinition definition=definition();
        definition.setStatus(TriggerDefinitionStatus.INACTIVE);
        when(triggerDefinitionRepository.findById(1L)).thenReturn(Mono.just(definition));
        // evaluate() builds the observationRepository publisher via
        // validateDefinition(...).then(evaluateDefinition(...)); the Java call
        // constructing evaluateDefinition's Flux argument happens eagerly even though
        // reactor never subscribes to it once validateDefinition errors first, so the
        // mock still needs a non-null stub to avoid an NPE while assembling the chain.
        when(observationRepository.findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
                any(),any(),any(),any(),any())).thenReturn(Flux.empty());

        StepVerifier.create(service.evaluate(1L,null,5L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void evaluate_whenEvaluationEndIsFuture_shouldReturnValidationError() {
        EvaluateTriggerRequest request=EvaluateTriggerRequest.builder()
                .evaluationEnd(LocalDateTime.now().plusHours(1)).build();

        StepVerifier.create(service.evaluate(1L,request,5L))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(triggerDefinitionRepository);
    }

    private void mockEvaluateCommon(Flux<Observation> observations) {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(observationRepository
                .findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
                        eq(1L),eq(MeasurementType.RAINFALL),eq(ObservationStatus.VALIDATED),
                        any(LocalDateTime.class),any(LocalDateTime.class)))
                .thenReturn(observations);
        when(triggerEvaluationRepository.save(any(TriggerEvaluation.class)))
                .thenAnswer(invocation->{
                    TriggerEvaluation saved=invocation.getArgument(0);
                    saved.setId(1L);
                    return Mono.just(saved);
                });
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone()));
        when(triggerEventRepository.findByTriggerEvaluationId(1L)).thenReturn(Mono.empty());
    }

    private void mockEvaluationResponse() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone()));
        when(triggerEventRepository.findByTriggerEvaluationId(1L)).thenReturn(Mono.empty());
    }

    private TriggerDefinition definition() {
        return TriggerDefinition.builder().id(1L).definitionCode("DEF-001")
                .definitionName("Definition").perilId(1L).locationZoneId(1L)
                .productId(1L).measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM).aggregationType(AggregationType.SUM)
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL)
                .thresholdValue(new BigDecimal("150.00")).observationWindowHours(24)
                .minimumObservationCount(1).status(TriggerDefinitionStatus.ACTIVE)
                .effectiveFrom(LocalDate.now().minusDays(1))
                .effectiveTo(LocalDate.now().plusDays(30)).build();
    }

    private Observation observation(BigDecimal value) {
        return Observation.builder().id(1L).locationZoneId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM).observedValue(value)
                .observedAt(LocalDateTime.now().minusHours(1))
                .status(ObservationStatus.VALIDATED).build();
    }

    private TriggerEvaluation evaluation(TriggerEvaluationResult result) {
        return TriggerEvaluation.builder().id(1L).evaluationReference("EVAL-001")
                .triggerDefinitionId(1L).locationZoneId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .windowStart(LocalDateTime.now().minusHours(24))
                .windowEnd(LocalDateTime.now()).observationCount(1)
                .aggregatedValue(new BigDecimal("160.00"))
                .thresholdValue(new BigDecimal("150.00"))
                .comparisonOperator(ComparisonOperator.GREATER_THAN_OR_EQUAL)
                .result(result).evaluatedBy(5L).evaluatedAt(LocalDateTime.now()).build();
    }

    private LocationZone zone() {
        return LocationZone.builder().id(1L).zoneCode("ZONE_1")
                .zoneName("Zone 1").build();
    }
}