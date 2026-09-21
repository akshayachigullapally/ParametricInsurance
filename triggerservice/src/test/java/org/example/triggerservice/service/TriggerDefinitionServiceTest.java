package org.example.triggerservice.service;

import org.example.triggerservice.dto.TriggerDefinitionRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.*;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void getAll_shouldReturnEnrichedDefinitions() {
        mockResponseReferences();
        when(triggerDefinitionRepository
                .findAllByStatusNotOrderByDefinitionNameAsc(TriggerDefinitionStatus.ARCHIVED))
                .thenReturn(Flux.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));

        StepVerifier.create(service.getAll())
                .assertNext(result->{
                    assertThat(result.getDefinitionCode()).isEqualTo("DEF_1");
                    assertThat(result.getPerilCode()).isEqualTo("FLOOD");
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                })
                .verifyComplete();
    }

    @Test
    void getActive_shouldReturnActiveDefinitions() {
        mockResponseReferences();
        when(triggerDefinitionRepository
                .findAllByStatusOrderByDefinitionNameAsc(TriggerDefinitionStatus.ACTIVE))
                .thenReturn(Flux.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));

        StepVerifier.create(service.getActive())
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(TriggerDefinitionStatus.ACTIVE))
                .verifyComplete();
    }

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
    void getById_whenDefinitionIsArchived_shouldReturnNotFound() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.ARCHIVED)));

        StepVerifier.create(service.getById(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getByPerilId_shouldReturnDefinitions() {
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril(PerilStatus.ACTIVE)));
        when(triggerDefinitionRepository
                .findAllByPerilIdAndStatusNotOrderByDefinitionNameAsc(
                        1L,TriggerDefinitionStatus.ARCHIVED))
                .thenReturn(Flux.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ACTIVE)));

        StepVerifier.create(service.getByPerilId(1L))
                .assertNext(result->assertThat(result.getPerilId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getByLocationZoneId_shouldReturnDefinitions() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ACTIVE)));
        when(triggerDefinitionRepository
                .findAllByLocationZoneIdAndStatusNotOrderByDefinitionNameAsc(
                        1L,TriggerDefinitionStatus.ARCHIVED))
                .thenReturn(Flux.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril(PerilStatus.ACTIVE)));

        StepVerifier.create(service.getByLocationZoneId(1L))
                .assertNext(result->assertThat(result.getLocationZoneId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getByProductId_shouldReturnDefinitions() {
        mockResponseReferences();
        when(triggerDefinitionRepository
                .findAllByProductIdAndStatusNotOrderByDefinitionNameAsc(
                        1L,TriggerDefinitionStatus.ARCHIVED))
                .thenReturn(Flux.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));

        StepVerifier.create(service.getByProductId(1L))
                .assertNext(result->assertThat(result.getProductId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void create_shouldSaveDraftDefinition() {
        TriggerDefinitionRequest request=request(" new def ");
        mockActiveReferences();
        when(triggerDefinitionRepository.existsByDefinitionCodeIgnoreCase("NEW_DEF"))
                .thenReturn(Mono.just(false));
        when(triggerDefinitionRepository.existsSimilarDefinition(1L,1L,1L,"RAINFALL"))
                .thenReturn(Mono.just(false));
        when(triggerDefinitionRepository.save(any(TriggerDefinition.class)))
                .thenAnswer(invocation->{
                    TriggerDefinition saved=invocation.getArgument(0);
                    saved.setId(1L);
                    return Mono.just(saved);
                });

        StepVerifier.create(service.create(request,10L))
                .assertNext(result->{
                    assertThat(result.getDefinitionCode()).isEqualTo("NEW_DEF");
                    assertThat(result.getStatus()).isEqualTo(TriggerDefinitionStatus.DRAFT);
                    assertThat(result.getMeasurementType()).isEqualTo(MeasurementType.RAINFALL);
                })
                .verifyComplete();
    }

    @Test
    void create_whenCodeExists_shouldReturnConflict() {
        mockActiveReferences();
        when(triggerDefinitionRepository.existsByDefinitionCodeIgnoreCase("DUPLICATE"))
                .thenReturn(Mono.just(true));
        when(triggerDefinitionRepository.existsSimilarDefinition(1L,1L,1L,"RAINFALL"))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.create(request("duplicate"),10L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void create_whenSimilarDefinitionExists_shouldReturnConflict() {
        mockActiveReferences();
        when(triggerDefinitionRepository.existsByDefinitionCodeIgnoreCase("NEW_DEF"))
                .thenReturn(Mono.just(false));
        when(triggerDefinitionRepository.existsSimilarDefinition(1L,1L,1L,"RAINFALL"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.create(request("new def"),10L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void create_whenDatesAreInvalid_shouldReturnValidationError() {
        TriggerDefinitionRequest request=request("new def");
        request.setEffectiveFrom(LocalDate.now());
        request.setEffectiveTo(LocalDate.now().minusDays(1));

        StepVerifier.create(Mono.defer(()->service.create(request,10L)))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(triggerDefinitionRepository,perilRepository,locationZoneRepository);
    }

    @Test
    void update_shouldUpdateInactiveDefinition() {
        TriggerDefinition existing=definition(1L,TriggerDefinitionStatus.INACTIVE);
        TriggerDefinitionRequest request=request("updated def");

        when(triggerDefinitionRepository.findById(1L)).thenReturn(Mono.just(existing));
        mockActiveReferences();
        when(triggerDefinitionRepository.existsByDefinitionCodeAndIdNot("UPDATED_DEF",1L))
                .thenReturn(Mono.just(false));
        when(triggerDefinitionRepository.existsSimilarDefinitionAndIdNot(
                1L,1L,1L,"RAINFALL",1L)).thenReturn(Mono.just(false));
        when(triggerDefinitionRepository.save(any(TriggerDefinition.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.update(1L,request,20L))
                .assertNext(result->{
                    assertThat(result.getDefinitionCode()).isEqualTo("UPDATED_DEF");
                    assertThat(result.getUpdatedBy()).isEqualTo(20L);
                })
                .verifyComplete();
    }

    @Test
    void update_whenDefinitionIsActive_shouldReturnConflict() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));

        StepVerifier.create(service.update(1L,request("updated"),20L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void updateStatus_shouldActivateDraftDefinition() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.DRAFT)));
        mockActiveReferences();
        when(triggerDefinitionRepository.save(any(TriggerDefinition.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.updateStatus(1L,"ACTIVE",20L))
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(TriggerDefinitionStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void updateStatus_shouldDeactivateActiveDefinition() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));
        when(triggerDefinitionRepository.save(any(TriggerDefinition.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        mockResponseReferences();

        StepVerifier.create(service.updateStatus(1L,"INACTIVE",20L))
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(TriggerDefinitionStatus.INACTIVE))
                .verifyComplete();
    }

    @Test
    void updateStatus_whenDraftRequested_shouldReturnValidationError() {
        StepVerifier.create(service.updateStatus(1L,"DRAFT",20L))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void archive_shouldArchiveInactiveDefinition() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.INACTIVE)));
        when(triggerDefinitionRepository.save(any(TriggerDefinition.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.archive(1L,20L)).verifyComplete();

        ArgumentCaptor<TriggerDefinition> captor=
                ArgumentCaptor.forClass(TriggerDefinition.class);
        verify(triggerDefinitionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus())
                .isEqualTo(TriggerDefinitionStatus.ARCHIVED);
    }

    @Test
    void archive_whenDefinitionIsActive_shouldReturnConflict() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition(1L,TriggerDefinitionStatus.ACTIVE)));

        StepVerifier.create(service.archive(1L,20L))
                .expectError(ConflictException.class)
                .verify();
    }

    private void mockActiveReferences() {
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril(PerilStatus.ACTIVE)));
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ACTIVE)));
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

    private TriggerDefinitionRequest request(String code) {
        return TriggerDefinitionRequest.builder().definitionCode(code)
                .definitionName(" Definition ").description(" Description ")
                .perilId(1L).locationZoneId(1L).productId(1L)
                .measurementType(" rainfall ").measurementUnit(" mm ")
                .aggregationType(" sum ").comparisonOperator(" greater_than ")
                .thresholdValue(new BigDecimal("100.00")).observationWindowHours(24)
                .minimumObservationCount(1).effectiveFrom(LocalDate.now())
                .effectiveTo(LocalDate.now().plusDays(30)).build();
    }
}