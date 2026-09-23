package org.example.triggerservice.repository;

import org.example.triggerservice.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TriggerEventRepositoryTest {

    @Autowired
    private TriggerEventRepository triggerEventRepository;

    @Autowired
    private TriggerEvaluationRepository triggerEvaluationRepository;

    @Autowired
    private TriggerDefinitionRepository triggerDefinitionRepository;

    @Autowired
    private PerilRepository perilRepository;

    @Autowired
    private LocationZoneRepository locationZoneRepository;

    @Autowired
    private ObservationRepository observationRepository;

    private Peril savedPeril;
    private LocationZone savedZone;
    private TriggerDefinition savedDefinition;
    private TriggerEvaluation savedEvaluation;

    @BeforeEach
    void setUp() {
        // Full dependency-order cleanup: other @SpringBootTest classes share the same
        // persistent H2 instance within one Maven fork, so every FK-dependent child
        // table must be cleared before its parents.
        triggerEventRepository.deleteAll().block();
        triggerEvaluationRepository.deleteAll().block();
        observationRepository.deleteAll().block();
        triggerDefinitionRepository.deleteAll().block();
        perilRepository.deleteAll().block();
        locationZoneRepository.deleteAll().block();

        savedPeril = perilRepository.save(Peril.builder()
                .perilCode("EVENT_PERIL")
                .perilName("Event Peril")
                .description("Event")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build()).block();

        savedZone = locationZoneRepository.save(LocationZone.builder()
                .zoneCode("EVENT_ZONE")
                .zoneName("Event Zone")
                .country("US")
                .state("CT")
                .district("Hartford")
                .latitude(new BigDecimal("41.7658000"))
                .longitude(new BigDecimal("-72.6734000"))
                .radiusKm(new BigDecimal("50.00"))
                .status(LocationZoneStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build()).block();

        savedDefinition = triggerDefinitionRepository.save(TriggerDefinition.builder()
                .definitionCode("EVENT_DEF")
                .definitionName("Event Definition")
                .description("Event Definition")
                .perilId(savedPeril.getId())
                .locationZoneId(savedZone.getId())
                .productId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM)
                .aggregationType(AggregationType.SUM)
                .comparisonOperator(ComparisonOperator.GREATER_THAN)
                .thresholdValue(new BigDecimal("100.00"))
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .status(TriggerDefinitionStatus.ACTIVE)
                .effectiveFrom(java.time.LocalDate.now())
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build()).block();

        savedEvaluation = triggerEvaluationRepository.save(TriggerEvaluation.builder()
                .evaluationReference("EVAL-EVT-001")
                .triggerDefinitionId(savedDefinition.getId())
                .locationZoneId(savedZone.getId())
                .measurementType(MeasurementType.RAINFALL)
                .windowStart(LocalDateTime.now().minusHours(24))
                .windowEnd(LocalDateTime.now())
                .observationCount(5)
                .aggregatedValue(new BigDecimal("120.00"))
                .thresholdValue(new BigDecimal("100.00"))
                .comparisonOperator(ComparisonOperator.GREATER_THAN)
                .result(TriggerEvaluationResult.MATCHED)
                .evaluatedBy(1L)
                .evaluatedAt(LocalDateTime.now())
                .build()).block();
    }

    private TriggerEvent createEvent(String reference, TriggerEventStatus status, LocalDateTime detectedAt) {
        return createEvent(reference, status, detectedAt, savedEvaluation.getId());
    }

    private TriggerEvent createEvent(String reference, TriggerEventStatus status, LocalDateTime detectedAt, Long evaluationId) {
        return TriggerEvent.builder()
                .eventReference(reference)
                .triggerDefinitionId(savedDefinition.getId())
                .triggerEvaluationId(evaluationId)
                .perilId(savedPeril.getId())
                .locationZoneId(savedZone.getId())
                .productId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM)
                .aggregatedValue(new BigDecimal("120.00"))
                .thresholdValue(new BigDecimal("100.00"))
                .windowStart(LocalDateTime.now().minusHours(24))
                .windowEnd(LocalDateTime.now())
                .status(status)
                .detectedAt(detectedAt)
                .build();
    }

    @Test
    void shouldSaveAndFindTriggerEventById() {
        TriggerEvent event = createEvent("EVT-001", TriggerEventStatus.DETECTED, LocalDateTime.now());

        StepVerifier.create(triggerEventRepository.save(event))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getEventReference()).isEqualTo("EVT-001");
                    assertThat(saved.getStatus()).isEqualTo(TriggerEventStatus.DETECTED);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByEventReference() {
        triggerEventRepository.save(createEvent("REF-EVT", TriggerEventStatus.DETECTED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.existsByEventReference("REF-EVT"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldFindTriggerEventByTriggerEvaluationId() {
        triggerEventRepository.save(createEvent("EVAL-EVT", TriggerEventStatus.DETECTED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findByTriggerEvaluationId(savedEvaluation.getId()))
                .assertNext(event -> assertThat(event.getTriggerEvaluationId()).isEqualTo(savedEvaluation.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByOrderByDetectedAtDesc() {
        // trigger_evaluation_id is unique on trigger_events, so each event needs its own evaluation row.
        TriggerEvaluation secondEvaluation = triggerEvaluationRepository.save(TriggerEvaluation.builder()
                .evaluationReference("EVAL-EVT-002")
                .triggerDefinitionId(savedDefinition.getId())
                .locationZoneId(savedZone.getId())
                .measurementType(MeasurementType.RAINFALL)
                .windowStart(LocalDateTime.now().minusHours(24))
                .windowEnd(LocalDateTime.now())
                .observationCount(5)
                .aggregatedValue(new BigDecimal("120.00"))
                .thresholdValue(new BigDecimal("100.00"))
                .comparisonOperator(ComparisonOperator.GREATER_THAN)
                .result(org.example.triggerservice.model.TriggerEvaluationResult.MATCHED)
                .evaluatedBy(1L)
                .evaluatedAt(LocalDateTime.now())
                .build()).block();

        triggerEventRepository.save(createEvent("EVT-OLD", TriggerEventStatus.DETECTED, LocalDateTime.now().minusHours(1), savedEvaluation.getId())).block();
        triggerEventRepository.save(createEvent("EVT-NEW", TriggerEventStatus.DETECTED, LocalDateTime.now(), secondEvaluation.getId())).block();

        StepVerifier.create(triggerEventRepository.findAllByOrderByDetectedAtDesc())
                .assertNext(e -> assertThat(e.getEventReference()).isEqualTo("EVT-NEW"))
                .assertNext(e -> assertThat(e.getEventReference()).isEqualTo("EVT-OLD"))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusOrderByDetectedAtDesc() {
        triggerEventRepository.save(createEvent("VER-EVT", TriggerEventStatus.VERIFIED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findAllByStatusOrderByDetectedAtDesc(TriggerEventStatus.VERIFIED))
                .assertNext(e -> assertThat(e.getStatus()).isEqualTo(TriggerEventStatus.VERIFIED))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByTriggerDefinitionIdOrderByDetectedAtDesc() {
        triggerEventRepository.save(createEvent("DEF-EVT", TriggerEventStatus.DETECTED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findAllByTriggerDefinitionIdOrderByDetectedAtDesc(savedDefinition.getId()))
                .assertNext(e -> assertThat(e.getTriggerDefinitionId()).isEqualTo(savedDefinition.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByProductIdOrderByDetectedAtDesc() {
        triggerEventRepository.save(createEvent("PROD-EVT", TriggerEventStatus.DETECTED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findAllByProductIdOrderByDetectedAtDesc(1L))
                .assertNext(e -> assertThat(e.getProductId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByLocationZoneIdOrderByDetectedAtDesc() {
        triggerEventRepository.save(createEvent("ZONE-EVT", TriggerEventStatus.DETECTED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findAllByLocationZoneIdOrderByDetectedAtDesc(savedZone.getId()))
                .assertNext(e -> assertThat(e.getLocationZoneId()).isEqualTo(savedZone.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByProductIdAndStatusOrderByDetectedAtDesc() {
        triggerEventRepository.save(createEvent("PS-EVT", TriggerEventStatus.VERIFIED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEventRepository.findAllByProductIdAndStatusOrderByDetectedAtDesc(1L, TriggerEventStatus.VERIFIED))
                .assertNext(e -> {
                    assertThat(e.getProductId()).isEqualTo(1L);
                    assertThat(e.getStatus()).isEqualTo(TriggerEventStatus.VERIFIED);
                })
                .verifyComplete();
    }
}
