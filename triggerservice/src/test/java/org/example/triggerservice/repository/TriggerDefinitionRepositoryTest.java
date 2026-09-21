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

    private Peril savedPeril;
    private LocationZone savedZone;

    @BeforeEach
    void setUp() {
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
    void shouldSaveAndFindTriggerDefinitionById() {
        TriggerDefinition def = createDefinition("DEF_001", TriggerDefinitionStatus.DRAFT);

        StepVerifier.create(triggerDefinitionRepository.save(def))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getDefinitionCode()).isEqualTo("DEF_001");
                    assertThat(saved.getStatus()).isEqualTo(TriggerDefinitionStatus.DRAFT);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByDefinitionCode() {
        triggerDefinitionRepository.save(createDefinition("DUP_DEF", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.existsByDefinitionCodeIgnoreCase("dup_def"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusNotOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("ACTIVE_DEF", TriggerDefinitionStatus.ACTIVE)).block();
        triggerDefinitionRepository.save(createDefinition("ARCHIVED_DEF", TriggerDefinitionStatus.ARCHIVED)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByStatusNotOrderByDefinitionNameAsc(TriggerDefinitionStatus.ARCHIVED))
                .assertNext(d -> assertThat(d.getStatus()).isNotEqualTo(TriggerDefinitionStatus.ARCHIVED))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("ACTIVE_DEF2", TriggerDefinitionStatus.ACTIVE)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByStatusOrderByDefinitionNameAsc(TriggerDefinitionStatus.ACTIVE))
                .assertNext(d -> assertThat(d.getStatus()).isEqualTo(TriggerDefinitionStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByPerilIdAndStatusNotOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("PERIL_DEF", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByPerilIdAndStatusNotOrderByDefinitionNameAsc(
                savedPeril.getId(), TriggerDefinitionStatus.ARCHIVED))
                .assertNext(d -> assertThat(d.getPerilId()).isEqualTo(savedPeril.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByLocationZoneIdAndStatusNotOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("ZONE_DEF", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByLocationZoneIdAndStatusNotOrderByDefinitionNameAsc(
                savedZone.getId(), TriggerDefinitionStatus.ARCHIVED))
                .assertNext(d -> assertThat(d.getLocationZoneId()).isEqualTo(savedZone.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByProductIdAndStatusNotOrderByDefinitionNameAsc() {
        triggerDefinitionRepository.save(createDefinition("PRODUCT_DEF", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.findAllByProductIdAndStatusNotOrderByDefinitionNameAsc(
                1L, TriggerDefinitionStatus.ARCHIVED))
                .assertNext(d -> assertThat(d.getProductId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByDefinitionCodeAndIdNot() {
        TriggerDefinition saved = triggerDefinitionRepository.save(
                createDefinition("CODE_CHECK", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.existsByDefinitionCodeAndIdNot("code_check", saved.getId()))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldFindByDefinitionCodeIgnoreCase() {
        triggerDefinitionRepository.save(createDefinition("FIND_ME", TriggerDefinitionStatus.DRAFT)).block();

        StepVerifier.create(triggerDefinitionRepository.findByDefinitionCodeIgnoreCase("find_me"))
                .assertNext(found -> assertThat(found.getDefinitionCode()).isEqualTo("FIND_ME"))
                .verifyComplete();
    }
}
