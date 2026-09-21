package org.example.triggerservice.repository;

import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
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
class LocationZoneRepositoryTest {

    @Autowired
    private LocationZoneRepository locationZoneRepository;

    @BeforeEach
    void setUp() {
        locationZoneRepository.deleteAll().block();
    }

    @Test
    void shouldSaveAndFindLocationZoneById() {
        LocationZone zone = LocationZone.builder()
                .zoneCode("ZONE_001")
                .zoneName("Test Zone")
                .country("US")
                .state("CT")
                .district("Hartford")
                .postalCode("06103")
                .latitude(new BigDecimal("41.7658000"))
                .longitude(new BigDecimal("-72.6734000"))
                .radiusKm(new BigDecimal("50.00"))
                .status(LocationZoneStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        StepVerifier.create(locationZoneRepository.save(zone))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getZoneCode()).isEqualTo("ZONE_001");
                    assertThat(saved.getZoneName()).isEqualTo("Test Zone");
                    assertThat(saved.getStatus()).isEqualTo(LocationZoneStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByZoneCode() {
        LocationZone zone = LocationZone.builder()
                .zoneCode("DUP_ZONE")
                .zoneName("Duplicate Zone")
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
                .build();

        locationZoneRepository.save(zone).block();

        StepVerifier.create(locationZoneRepository.existsByZoneCodeIgnoreCase("dup_zone"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldFindByZoneCodeIgnoreCase() {
        LocationZone zone = LocationZone.builder()
                .zoneCode("FIND_ZONE")
                .zoneName("Find Zone")
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
                .build();

        locationZoneRepository.save(zone).block();

        StepVerifier.create(locationZoneRepository.findByZoneCodeIgnoreCase("find_zone"))
                .assertNext(found -> assertThat(found.getZoneCode()).isEqualTo("FIND_ZONE"))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusNotOrderByZoneNameAsc() {
        LocationZone active = LocationZone.builder()
                .zoneCode("ACTIVE_Z")
                .zoneName("Active Zone")
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
                .build();

        LocationZone archived = LocationZone.builder()
                .zoneCode("ARCHIVED_Z")
                .zoneName("Archived Zone")
                .country("US")
                .state("CT")
                .district("Hartford")
                .latitude(new BigDecimal("41.7658000"))
                .longitude(new BigDecimal("-72.6734000"))
                .radiusKm(new BigDecimal("50.00"))
                .status(LocationZoneStatus.ARCHIVED)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        locationZoneRepository.saveAll(java.util.List.of(active, archived)).blockLast();

        StepVerifier.create(locationZoneRepository.findAllByStatusNotOrderByZoneNameAsc(LocationZoneStatus.ARCHIVED))
                .assertNext(z -> assertThat(z.getStatus()).isNotEqualTo(LocationZoneStatus.ARCHIVED))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStateIgnoreCaseAndStatusOrderByZoneNameAsc() {
        LocationZone zone = LocationZone.builder()
                .zoneCode("STATE_ZONE")
                .zoneName("State Zone")
                .country("US")
                .state("California")
                .district("LA")
                .latitude(new BigDecimal("34.0522000"))
                .longitude(new BigDecimal("-118.2437000"))
                .radiusKm(new BigDecimal("25.00"))
                .status(LocationZoneStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        locationZoneRepository.save(zone).block();

        StepVerifier.create(locationZoneRepository.findAllByStateIgnoreCaseAndStatusOrderByZoneNameAsc("california", LocationZoneStatus.ACTIVE))
                .assertNext(z -> assertThat(z.getState()).isEqualTo("California"))
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByZoneCodeAndIdNot() {
        LocationZone zone = LocationZone.builder()
                .zoneCode("UNIQUE_Z")
                .zoneName("Unique Zone")
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
                .build();

        LocationZone saved = locationZoneRepository.save(zone).block();

        StepVerifier.create(locationZoneRepository.existsByZoneCodeAndIdNot("unique_z", saved.getId()))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }
}
