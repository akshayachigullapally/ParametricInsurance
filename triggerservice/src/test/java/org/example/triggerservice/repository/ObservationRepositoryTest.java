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
class ObservationRepositoryTest {

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private LocationZoneRepository locationZoneRepository;

    @Autowired
    private TriggerDefinitionRepository triggerDefinitionRepository;

    @Autowired
    private TriggerEvaluationRepository triggerEvaluationRepository;

    @Autowired
    private TriggerEventRepository triggerEventRepository;

    private LocationZone savedZone;

    @BeforeEach
    void setUp() {
        // Full dependency-order cleanup: other @SpringBootTest classes in this module
        // share the same persistent H2 instance within one Maven fork, and demo data
        // seeded at context startup references location_zones, so every FK-dependent
        // child table must be cleared before location_zones itself.
        observationRepository.deleteAll().block();
        triggerEventRepository.deleteAll().block();
        triggerEvaluationRepository.deleteAll().block();
        triggerDefinitionRepository.deleteAll().block();
        locationZoneRepository.deleteAll().block();

        savedZone = locationZoneRepository.save(LocationZone.builder()
                .zoneCode("OBS_ZONE")
                .zoneName("Observation Zone")
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

    private Observation createObservation(String reference, MeasurementType type, ObservationStatus status, LocalDateTime observedAt) {
        return Observation.builder()
                .observationReference(reference)
                .locationZoneId(savedZone.getId())
                .measurementType(type)
                .measurementUnit(MeasurementUnit.MM)
                .observedValue(new BigDecimal("25.50"))
                .observedAt(observedAt)
                .source(ObservationSource.MANUAL)
                .status(status)
                .receivedBy(1L)
                .receivedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldSaveAndFindObservationById() {
        Observation obs = createObservation("OBS-001", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now());

        StepVerifier.create(observationRepository.save(obs))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getObservationReference()).isEqualTo("OBS-001");
                    assertThat(saved.getStatus()).isEqualTo(ObservationStatus.VALIDATED);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByObservationReference() {
        observationRepository.save(createObservation("REF-001", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now())).block();

        StepVerifier.create(observationRepository.existsByObservationReference("REF-001"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsBySourceReference() {
        Observation obs = createObservation("SRC-001", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now());
        obs.setSourceReference("SOURCE-REF-001");
        observationRepository.save(obs).block();

        StepVerifier.create(observationRepository.existsBySourceReference("SOURCE-REF-001"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldFindAllByOrderByObservedAtDesc() {
        observationRepository.save(createObservation("OBS-A", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now().minusHours(1))).block();
        observationRepository.save(createObservation("OBS-B", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now())).block();

        StepVerifier.create(observationRepository.findAllByOrderByObservedAtDesc())
                .assertNext(obs -> assertThat(obs.getObservationReference()).isEqualTo("OBS-B"))
                .assertNext(obs -> assertThat(obs.getObservationReference()).isEqualTo("OBS-A"))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByLocationZoneIdOrderByObservedAtDesc() {
        observationRepository.save(createObservation("ZONE-OBS", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, LocalDateTime.now())).block();

        StepVerifier.create(observationRepository.findAllByLocationZoneIdOrderByObservedAtDesc(savedZone.getId()))
                .assertNext(obs -> assertThat(obs.getLocationZoneId()).isEqualTo(savedZone.getId()))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByMeasurementTypeOrderByObservedAtDesc() {
        observationRepository.save(createObservation("TEMP-OBS", MeasurementType.TEMPERATURE, ObservationStatus.VALIDATED, LocalDateTime.now())).block();

        StepVerifier.create(observationRepository.findAllByMeasurementTypeOrderByObservedAtDesc(MeasurementType.TEMPERATURE))
                .assertNext(obs -> assertThat(obs.getMeasurementType()).isEqualTo(MeasurementType.TEMPERATURE))
                .verifyComplete();
    }

    @Test
    void shouldFindObservationsInDateRange() {
        LocalDateTime now = LocalDateTime.now();
        observationRepository.save(createObservation("RANGE-OBS", MeasurementType.RAINFALL, ObservationStatus.VALIDATED, now.minusHours(2))).block();

        StepVerifier.create(observationRepository
                .findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
                        savedZone.getId(), MeasurementType.RAINFALL, ObservationStatus.VALIDATED,
                        now.minusHours(5), now))
                .assertNext(obs -> assertThat(obs.getLocationZoneId()).isEqualTo(savedZone.getId()))
                .verifyComplete();
    }
}
