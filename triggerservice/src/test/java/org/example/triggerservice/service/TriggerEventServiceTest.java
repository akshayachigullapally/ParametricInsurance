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
import org.mockito.ArgumentCaptor;
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
    @Mock private IdempotencyService idempotencyService;
    @Mock private NotificationService notificationService;
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
    void getByStatus_shouldParseStatusAndReturnEvents() {
        when(triggerEventRepository
                .findAllByStatusOrderByDetectedAtDesc(TriggerEventStatus.DETECTED))
                .thenReturn(Flux.just(event(TriggerEventStatus.DETECTED)));
        mockResponseReferences();

        StepVerifier.create(service.getByStatus(" detected "))
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(TriggerEventStatus.DETECTED))
                .verifyComplete();
    }

    @Test
    void getByStatus_whenStatusIsInvalid_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(()->service.getByStatus("UNKNOWN").then()))
                .expectError(ValidationException.class)
                .verify();
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
    void getById_whenEventDoesNotExist_shouldReturnNotFound() {
        when(triggerEventRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.getById(99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void startValidation_shouldChangeDetectedEventToUnderValidation() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.DETECTED)));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        mockResponseReferences();

        StepVerifier.create(service.startValidation(1L,10L))
                .assertNext(result->{
                    assertThat(result.getStatus())
                            .isEqualTo(TriggerEventStatus.UNDER_VALIDATION);
                    assertThat(result.getReviewedBy()).isEqualTo(10L);
                })
                .verifyComplete();
    }

    @Test
    void startValidation_whenEventIsNotDetected_shouldReturnConflict() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.VERIFIED)));

        StepVerifier.create(service.startValidation(1L,10L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void verify_shouldSaveVerifiedEventAndSendNotification() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.UNDER_VALIDATION)));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(notificationService.notifyTriggerVerified(null,1L,"EVT-1"))
                .thenReturn(Mono.empty());
        mockResponseReferences();

        StepVerifier.create(service.verify(1L," Looks good ",10L))
                .assertNext(result->{
                    assertThat(result.getStatus()).isEqualTo(TriggerEventStatus.VERIFIED);
                    assertThat(result.getReviewNotes()).isEqualTo("Looks good");
                })
                .verifyComplete();

        verify(notificationService).notifyTriggerVerified(null,1L,"EVT-1");
    }

    @Test
    void verify_whenEventIsNotUnderValidation_shouldReturnConflict() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.DETECTED)));

        StepVerifier.create(service.verify(1L,"Valid notes",10L))
                .expectError(ConflictException.class)
                .verify();

        verifyNoInteractions(notificationService);
    }

    @Test
    void verify_whenNotesAreBlank_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(()->service.verify(1L," ",10L)))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(triggerEventRepository,notificationService);
    }

    @Test
    void reject_shouldSaveRejectedEventAndSendNotification() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.UNDER_VALIDATION)));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(notificationService.notifyTriggerRejected(null,1L,"EVT-1"))
                .thenReturn(Mono.empty());
        mockResponseReferences();

        StepVerifier.create(service.reject(1L," Invalid event ",10L))
                .assertNext(result->{
                    assertThat(result.getStatus()).isEqualTo(TriggerEventStatus.REJECTED);
                    assertThat(result.getReviewNotes()).isEqualTo("Invalid event");
                })
                .verifyComplete();

        verify(notificationService).notifyTriggerRejected(null,1L,"EVT-1");
    }

    @Test
    void processEvent_whenNotDuplicate_shouldProcessAndRecordIdempotency() {
        TriggerEvent event=event(TriggerEventStatus.VERIFIED);
        String key=IdempotencyService.buildTriggerEventKey(
                event.getTriggerDefinitionId(),
                event.getWindowStart(),
                event.getWindowEnd()
        );

        IdempotencyRecord record=IdempotencyRecord.builder()
                .id(1L)
                .idempotencyKey(key)
                .operationType("PROCESSED_EVENT")
                .resourceId(1L)
                .createdAt(LocalDateTime.now())
                .build();

        when(triggerEventRepository.findById(1L)).thenReturn(Mono.just(event));
        when(idempotencyService.isDuplicate(key)).thenReturn(Mono.just(false));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(idempotencyService.record(key,"PROCESSED_EVENT",1L))
                .thenReturn(Mono.just(record));

        mockResponseReferences();

        StepVerifier.create(service.processEvent(1L," Processed ",10L))
                .assertNext(result->{
                    assertThat(result.getStatus()).isEqualTo(TriggerEventStatus.PROCESSED);
                    assertThat(result.getReviewNotes()).isEqualTo("Processed");
                    assertThat(result.getReviewedBy()).isEqualTo(10L);
                    assertThat(result.getReviewedAt()).isNotNull();
                })
                .verifyComplete();

        verify(triggerEventRepository).findById(1L);
        verify(idempotencyService).isDuplicate(key);
        verify(triggerEventRepository).save(argThat(saved->
                saved.getStatus()==TriggerEventStatus.PROCESSED&&
                        saved.getReviewedBy().equals(10L)&&
                        "Processed".equals(saved.getReviewNotes())
        ));
        verify(idempotencyService).record(key,"PROCESSED_EVENT",1L);
    }

    @Test
    void processEvent_whenDuplicate_shouldReturnConflict() {
        TriggerEvent event=event(TriggerEventStatus.VERIFIED);
        String key=IdempotencyService.buildTriggerEventKey(
                event.getTriggerDefinitionId(),event.getWindowStart(),event.getWindowEnd());

        when(triggerEventRepository.findById(1L)).thenReturn(Mono.just(event));
        when(idempotencyService.isDuplicate(key)).thenReturn(Mono.just(true));

        StepVerifier.create(service.processEvent(1L,"Processed",10L))
                .expectError(ConflictException.class)
                .verify();

        verify(triggerEventRepository,never()).save(any());
        verify(idempotencyService,never()).record(anyString(),anyString(),anyLong());
    }

    @Test
    void processEvent_whenEventIsNotVerified_shouldReturnConflict() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.DETECTED)));

        StepVerifier.create(service.processEvent(1L,"Processed",10L))
                .expectError(ConflictException.class)
                .verify();

        verifyNoInteractions(idempotencyService);
    }

    @Test
    void markEventProcessed_shouldUpdateVerifiedEvent() {
        when(triggerEventRepository.findById(1L))
                .thenReturn(Mono.just(event(TriggerEventStatus.VERIFIED)));
        when(triggerEventRepository.save(any(TriggerEvent.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.markEventProcessed(1L)).verifyComplete();

        ArgumentCaptor<TriggerEvent> captor=ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TriggerEventStatus.PROCESSED);
        assertThat(captor.getValue().getReviewNotes())
                .isEqualTo("Marked processed by financial service");
    }

    @Test
    void getByDefinitionId_shouldReturnEvents() {
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(triggerEventRepository
                .findAllByTriggerDefinitionIdOrderByDetectedAtDesc(1L))
                .thenReturn(Flux.just(event(TriggerEventStatus.DETECTED)));
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril()));
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone()));

        StepVerifier.create(service.getByDefinitionId(1L))
                .assertNext(result->assertThat(result.getTriggerDefinitionId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getByProductId_shouldReturnEvents() {
        when(triggerEventRepository.findAllByProductIdOrderByDetectedAtDesc(1L))
                .thenReturn(Flux.just(event(TriggerEventStatus.DETECTED)));
        mockResponseReferences();

        StepVerifier.create(service.getByProductId(1L))
                .assertNext(result->assertThat(result.getProductId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getByZoneId_shouldReturnEvents() {
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone()));
        when(triggerEventRepository
                .findAllByLocationZoneIdOrderByDetectedAtDesc(1L))
                .thenReturn(Flux.just(event(TriggerEventStatus.DETECTED)));
        when(triggerDefinitionRepository.findById(1L))
                .thenReturn(Mono.just(definition()));
        when(perilRepository.findById(1L)).thenReturn(Mono.just(peril()));

        StepVerifier.create(service.getByZoneId(1L))
                .assertNext(result->assertThat(result.getLocationZoneId()).isEqualTo(1L))
                .verifyComplete();
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
                .triggerDefinitionId(1L).triggerEvaluationId(1L).perilId(1L)
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