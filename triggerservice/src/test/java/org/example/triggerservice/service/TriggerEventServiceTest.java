package org.example.triggerservice.service;

import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.*;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerEventServiceTest {
    @Mock private TriggerEventRepository triggerEventRepository;
    @Mock private TriggerDefinitionRepository triggerDefinitionRepository;
    @Mock private PerilRepository perilRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @InjectMocks private TriggerEventService service;

    @Test
    void getAll_shouldReturnEnrichedEvents() {
        when(triggerEventRepository.findAllByOrderByDetectedAtDesc())
                .thenReturn(Flux.just(event(TriggerEventStatus.DETECTED)));
        mockResponseReferences();

        StepVerifier.create(service.getAll())
                .assertNext(result->{
                    assertThat(result.getEventReference()).isEqualTo("EVT-1");
                    assertThat(result.getDefinitionCode()).isEqualTo("DEF-1");
                    assertThat(result.getPerilCode()).isEqualTo("FLOOD");
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                })
                .verifyComplete();
    }

    @Test
    void getById_whenEventExists_shouldReturnEvent() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.DETECTED)));
        mockResponseReferences();

        StepVerifier.create(service.getById(1L))
                .assertNext(result->assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getById_whenEventMissing_shouldReturnNotFound() {
        when(triggerEventRepository.findById(9L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getById(9L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getById_whenIdInvalid_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(()->service.getById(0L)))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void getAll_whenReferencesMissing_shouldUseUnavailablePlaceholders() {
        when(triggerEventRepository.findAllByOrderByDetectedAtDesc())
                .thenReturn(Flux.just(event(TriggerEventStatus.VERIFIED)));
        when(triggerDefinitionRepository.findById(1L)).thenReturn(Mono.empty());
        when(perilRepository.findById(1L)).thenReturn(Mono.empty());
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getAll())
                .assertNext(result->{
                    assertThat(result.getDefinitionCode()).isEqualTo("UNAVAILABLE");
                    assertThat(result.getPerilName()).isEqualTo("Unavailable");
                    assertThat(result.getZoneCode()).isEqualTo("UNAVAILABLE");
                })
                .verifyComplete();
    }

    @Test
    void markEventProcessed_shouldMoveVerifiedEventToProcessed() {
        TriggerEvent event=event(TriggerEventStatus.VERIFIED);
        when(triggerEventRepository.findById(1L)).thenReturn(Mono.just(event));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(call->Mono.just(call.getArgument(0)));

        StepVerifier.create(service.markEventProcessed(1L)).verifyComplete();

        assertThat(event.getStatus()).isEqualTo(TriggerEventStatus.PROCESSED);
        assertThat(event.getReviewNotes()).contains("financial service");
    }

    @Test
    void markEventProcessed_whenEventNotVerified_shouldReturnConflict() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.PROCESSED)));

        StepVerifier.create(service.markEventProcessed(1L))
                .expectError(ConflictException.class)
                .verify();

        verify(triggerEventRepository,never()).save(any());
    }

    @Test
    void markEventProcessed_whenEventMissing_shouldReturnNotFound() {
        when(triggerEventRepository.findById(5L)).thenReturn(Mono.empty());

        StepVerifier.create(service.markEventProcessed(5L))
                .expectError(NotFoundException.class)
                .verify();
    }

    private void mockResponseReferences() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril()));
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone()));
    }

    private TriggerEvent event(TriggerEventStatus status) {
        LocalDateTime end=LocalDateTime.of(2026,9,15,12,0);
        return TriggerEvent.builder().id(1L).eventReference("EVT-1")
                .triggerDefinitionId(1L).perilId(1L)
                .locationZoneId(1L).productId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM)
                .aggregatedValue(new BigDecimal("160.00"))
                .thresholdValue(new BigDecimal("150.00"))
                .windowStart(end.minusHours(24)).windowEnd(end).status(status)
                .detectedAt(end).build();
    }

    private TriggerDefinition definition() {
        return TriggerDefinition.builder().id(1L).definitionCode("DEF-1")
                .definitionName("Definition 1").build();
    }

    private Peril peril() {
        return Peril.builder().id(1L).perilCode("FLOOD")
                .perilName("Flood").build();
    }

    private LocationZone zone() {
        return LocationZone.builder().id(1L).zoneCode("ZONE_1")
                .zoneName("Zone 1").build();
    }
}