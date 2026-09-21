package org.example.triggerservice.repository;

import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
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
class PerilRepositoryTest {

    @Autowired
    private PerilRepository perilRepository;

    @BeforeEach
    void setUp() {
        perilRepository.deleteAll().block();
    }

    @Test
    void shouldSaveAndFindPerilById() {
        Peril peril = Peril.builder()
                .perilCode("RAINFALL")
                .perilName("Excess Rainfall")
                .description("Rainfall exceeds threshold")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        StepVerifier.create(perilRepository.save(peril))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getPerilCode()).isEqualTo("RAINFALL");
                    assertThat(saved.getPerilName()).isEqualTo("Excess Rainfall");
                    assertThat(saved.getStatus()).isEqualTo(PerilStatus.ACTIVE);
                })
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByPerilCode() {
        Peril peril = Peril.builder()
                .perilCode("DROUGHT")
                .perilName("Drought")
                .description("Low rainfall")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        perilRepository.save(peril).block();

        StepVerifier.create(perilRepository.existsByPerilCodeIgnoreCase("drought"))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }

    @Test
    void shouldReturnFalseForNonExistentPerilCode() {
        StepVerifier.create(perilRepository.existsByPerilCodeIgnoreCase("NONEXISTENT"))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldFindByPerilCodeIgnoreCase() {
        Peril peril = Peril.builder()
                .perilCode("WIND")
                .perilName("High Wind")
                .description("Wind speed exceeds threshold")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        perilRepository.save(peril).block();

        StepVerifier.create(perilRepository.findByPerilCodeIgnoreCase("wind"))
                .assertNext(found -> assertThat(found.getPerilCode()).isEqualTo("WIND"))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusNotOrderByPerilNameAsc() {
        Peril active = Peril.builder()
                .perilCode("ACTIVE_PERIL")
                .perilName("Active Peril")
                .description("Active")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        Peril inactive = Peril.builder()
                .perilCode("INACTIVE_PERIL")
                .perilName("Inactive Peril")
                .description("Inactive")
                .status(PerilStatus.INACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        Peril archived = Peril.builder()
                .perilCode("ARCHIVED_PERIL")
                .perilName("Archived Peril")
                .description("Archived")
                .status(PerilStatus.ARCHIVED)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        perilRepository.saveAll(java.util.List.of(active, inactive, archived)).blockLast();

        StepVerifier.create(perilRepository.findAllByStatusNotOrderByPerilNameAsc(PerilStatus.ARCHIVED))
                .assertNext(p -> assertThat(p.getStatus()).isNotEqualTo(PerilStatus.ARCHIVED))
                .assertNext(p -> assertThat(p.getStatus()).isNotEqualTo(PerilStatus.ARCHIVED))
                .verifyComplete();
    }

    @Test
    void shouldFindAllByStatusOrderByPerilNameAsc() {
        Peril active = Peril.builder()
                .perilCode("ACTIVE_PERIL2")
                .perilName("Active Peril 2")
                .description("Active")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        perilRepository.save(active).block();

        StepVerifier.create(perilRepository.findAllByStatusOrderByPerilNameAsc(PerilStatus.ACTIVE))
                .assertNext(p -> assertThat(p.getStatus()).isEqualTo(PerilStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void shouldCheckExistsByPerilCodeAndIdNot() {
        Peril peril = Peril.builder()
                .perilCode("UNIQUE_CODE")
                .perilName("Unique Peril")
                .description("Unique")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        Peril saved = perilRepository.save(peril).block();

        StepVerifier.create(perilRepository.existsByPerilCodeAndIdNot("unique_code", saved.getId()))
                .assertNext(exists -> assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void shouldReturnTrueForExistsByPerilCodeAndIdNotWithDifferentCode() {
        Peril peril = Peril.builder()
                .perilCode("EXISTING_CODE")
                .perilName("Existing Peril")
                .description("Existing")
                .status(PerilStatus.ACTIVE)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedBy(1L)
                .updatedAt(LocalDateTime.now())
                .build();

        perilRepository.save(peril).block();

        StepVerifier.create(perilRepository.existsByPerilCodeAndIdNot("existing_code", 999L))
                .assertNext(exists -> assertThat(exists).isTrue())
                .verifyComplete();
    }
}
