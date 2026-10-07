package org.example.triggerservice.repository;

import org.example.triggerservice.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TriggerDefinitionRepositoryTest {

    @Autowired
    private TriggerDefinitionRepository triggerDefinitionRepository;

    @Autowired
    private PerilRepository perilRepository;

    @Autowired
    private LocationZoneRepository locationZoneRepository;

    @Autowired
    private TriggerEvaluationRepository triggerEvaluationRepository;

    @Autowired
    private TriggerEventRepository triggerEventRepository;

    @Autowired
    private ObservationRepository observationRepository;

    private Peril savedPeril;
    private LocationZone savedZone;

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

        savedPeril = perilRepository.save(Peril.builder()
                .perilCode("TEST_PERIL")
                .perilName("Test Peril")
                .description("Test")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build()).block();

        savedZone = locationZoneRepository.save(LocationZone.builder()
                .zoneCode("TEST_ZONE")
                .zoneName("Test Zone")
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
    }

    private TriggerDefinition createDefinition(String code, TriggerDefinitionStatus status) {
        return TriggerDefinition.builder()
                .definitionCode(code)
                .definitionName("Test Definition " + code)
                .description("Description for " + code)
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
                .status(status)
                .effectiveFrom(LocalDate.now())
                .effectiveTo(LocalDate.now().plusYears(1))
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldFindAllByPerilIdAndStatusNotOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("PERIL_DEF", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByPerilIdAndStatusNotOrderByDefinitionNameAsc(
                savedPeril.getId(), TriggerDefinitionStatus.ARCHIVED))
                .assertNext(d -> assertThat(d.getPerilId()).isEqualTo(savedPeril.getId()))
                .verifyComplete();
    }
}
