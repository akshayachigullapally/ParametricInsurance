package org.example.triggerservice.service;

import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.*;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.exception.NotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerDefinitionServiceTest {
    @Mock private TriggerDefinitionRepository triggerDefinitionRepository;
    @Mock private PerilRepository perilRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @InjectMocks private TriggerDefinitionService service;

    @Test
    void getById_whenDefinitionExists_shouldReturnDefinition() {
        mockResponseReferences();
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.DRAFT)));

        StepVerifier.create(service.getById(1L))
                .assertNext(result->assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getById_whenDefinitionArchived_shouldReturnNotFound() {
        when(triggerDefinitionRepository.findById(2L))
                .thenReturn(Mono.just(definition(2L,TriggerDefinitionStatus.ARCHIVED)));

        StepVerifier.create(service.getById(2L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getById_whenDefinitionMissing_shouldReturnNotFound() {
        when(triggerDefinitionRepository.findById(3L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getById(3L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getById_whenIdInvalid_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(()->service.getById(-1L)))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getById_whenReferencesMissing_shouldUseUnavailablePlaceholders() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));
        when(perilRepository.findById(1L)).thenReturn(Mono.empty());
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getById(1L))
                .assertNext(result->{
                    assertThat(result.getPerilCode()).isEqualTo("UNAVAILABLE");
                    assertThat(result.getZoneName()).isEqualTo("Unavailable");
                    assertThat(result.getThresholdValue()).isEqualByComparingTo("100.00");
                })
                .verifyComplete();
    }

    private void mockResponseReferences() {
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril(PerilStatus.ACTIVE)));
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ACTIVE)));
    }

    private Peril peril(PerilStatus status) {
        return Peril.builder().id(1L).perilCode("FLOOD").perilName("Flood")
                .status(status).build();
    }

    private LocationZone zone(LocationZoneStatus status) {
        return LocationZone.builder().id(1L).zoneCode("ZONE_1").zoneName("Zone 1")
                .status(status).build();
    }

    private TriggerDefinition definition(Long id,TriggerDefinitionStatus status) {
        LocalDateTime now=LocalDateTime.now();
        return TriggerDefinition.builder().id(id).definitionCode("DEF_"+id)
                .definitionName("Definition "+id).description("Description")
                .perilId(1L).locationZoneId(1L).productId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM).aggregationType(AggregationType.SUM)
                .comparisonOperator(ComparisonOperator.GREATER_THAN)
                .thresholdValue(new BigDecimal("100.00")).observationWindowHours(24)
                .minimumObservationCount(1).status(status)
                .effectiveFrom(LocalDate.now().minusDays(1))
                .effectiveTo(LocalDate.now().plusDays(30)).createdBy(1L)
                .createdAt(now).updatedBy(1L).updatedAt(now).build();
    }
}