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
class TriggerEvaluationRepositoryTest {

    @Autowired
    private TriggerEvaluationRepository triggerEvaluationRepository;

    @Autowired
    private TriggerDefinitionRepository triggerDefinitionRepository;

    @Autowired
    private PerilRepository perilRepository;

    @Autowired
    private LocationZoneRepository locationZoneRepository;

    @Autowired
    private TriggerEventRepository triggerEventRepository;

    @Autowired
    private ObservationRepository observationRepository;

    private TriggerDefinition savedDefinition;

    @BeforeEach
    void setUp() {
        // Full dependency-order cleanup: other @SpringBootTest classes share the same
        // persistent H2 instance within one Maven fork, and demo data seeded at
        // context startup references perils/location_zones, so every FK-dependent
        // child table must be cleared first.
        triggerEventRepository.deleteAll().block();
        triggerEvaluationRepository.deleteAll().block();
        observationRepository.deleteAll().block();
        triggerDefinitionRepository.deleteAll().block();
        perilRepository.deleteAll().block();
        locationZoneRepository.deleteAll().block();

        Peril peril = perilRepository.save(Peril.builder()
                .perilCode("EVAL_PERIL")
                .perilName("Eval Peril")
                .description("Eval")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build()).block();

        LocationZone zone = locationZoneRepository.save(LocationZone.builder()
                .zoneCode("EVAL_ZONE")
                .zoneName("Eval Zone")
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
                .definitionCode("EVAL_DEF")
                .definitionName("Eval Definition")
                .description("Eval Definition")
                .perilId(peril.getId())
                .locationZoneId(zone.getId())
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
    }

    private TriggerEvaluation createEvaluation(String reference, TriggerEvaluationResult result, LocalDateTime evaluatedAt) {
        return TriggerEvaluation.builder()
                .evaluationReference(reference)
                .triggerDefinitionId(savedDefinition.getId())
                .locationZoneId(savedDefinition.getLocationZoneId())
                .measurementType(MeasurementType.RAINFALL)
                .windowStart(LocalDateTime.now().minusHours(24))
                .windowEnd(LocalDateTime.now())
                .observationCount(5)
                .aggregatedValue(new BigDecimal("120.00"))
                .thresholdValue(new BigDecimal("100.00"))
                .comparisonOperator(ComparisonOperator.GREATER_THAN)
                .result(result)
                .evaluatedBy(1L)
                .evaluatedAt(evaluatedAt)
                .build();
    }

    @Test
    void shouldSaveAndFindTriggerEvaluationById() {
        TriggerEvaluation eval = createEvaluation("EVAL-001", TriggerEvaluationResult.MATCHED, LocalDateTime.now());

        StepVerifier.create(triggerEvaluationRepository.save(eval))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getEvaluationReference()).isEqualTo("EVAL-001");
                    assertThat(saved.getResult()).isEqualTo(TriggerEvaluationResult.MATCHED);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByEvaluationReference() {
        triggerEvaluationRepository.save(createEvaluation("REF-EVAL", TriggerEvaluationResult.MATCHED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEvaluationRepository.existsByEvaluationReference("REF-EVAL"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldFindAllByOrderByEvaluatedAtDesc() {
        triggerEvaluationRepository.save(createEvaluation("EVAL-OLD", TriggerEvaluationResult.MATCHED, LocalDateTime.now().minusHours(1))).block();
        triggerEvaluationRepository.save(createEvaluation("EVAL-NEW", TriggerEvaluationResult.NOT_MATCHED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEvaluationRepository.findAllByOrderByEvaluatedAtDesc())
                .assertNext(e -> assertThat(e.getEvaluationReference()).isEqualTo("EVAL-NEW"))
                .assertNext(e -> assertThat(e.getEvaluationReference()).isEqualTo("EVAL-OLD"))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByTriggerDefinitionIdOrderByEvaluatedAtDesc() {
        triggerEvaluationRepository.save(createEvaluation("DEF-EVAL", TriggerEvaluationResult.MATCHED, LocalDateTime.now())).block();

        StepVerifier.create(triggerEvaluationRepository.findAllByTriggerDefinitionIdOrderByEvaluatedAtDesc(savedDefinition.getId()))
                .assertNext(e -> assertThat(e.getTriggerDefinitionId()).isEqualTo(savedDefinition.getId()))
                .verifyComplete();
    }
}
