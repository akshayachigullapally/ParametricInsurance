package org.example.triggerservice.service;

import org.example.triggerservice.dto.CreateObservationRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.MeasurementUnit;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObservationServiceTest {
    @Mock private ObservationRepository observationRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @InjectMocks private ObservationService observationService;

    @Test
    void getById_whenIdIsNull_shouldThrowValidationException() {
        assertThatThrownBy(()->observationService.getById(null))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Observation ID must be greater than zero"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void getByMeasurementType_whenTypeIsBlank_shouldThrowValidationException() {
        assertThatThrownBy(()->observationService.getByMeasurementType(" "))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Measurement type is required"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenRequestIsValidAndHasSourceReference_shouldSaveObservation() {
        CreateObservationRequest request=request(
                " rainfall ",
                " mm ",
                " manual ",
                new BigDecimal("25.50")
        );
        request.setSourceReference(" REF-001 ");

        LocationZone zone=zone(LocationZoneStatus.ACTIVE);

        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone),Mono.just(zone));
        when(observationRepository.existsBySourceReference("REF-001"))
                .thenReturn(Mono.just(false));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->{
                    Observation saved=invocation.getArgument(0);
                    saved.setId(1L);
                    return Mono.just(saved);
                });

        StepVerifier.create(observationService.create(request,10L))
                .assertNext(result->{
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getObservationReference()).startsWith("OBS-");
                    assertThat(result.getLocationZoneId()).isEqualTo(1L);
                    assertThat(result.getMeasurementType()).isEqualTo(MeasurementType.RAINFALL);
                    assertThat(result.getMeasurementUnit()).isEqualTo(MeasurementUnit.MM);
                    assertThat(result.getObservedValue())
                            .isEqualByComparingTo("25.50");
                    assertThat(result.getSource()).isEqualTo(ObservationSource.MANUAL);
                    assertThat(result.getSourceReference()).isEqualTo("REF-001");
                    assertThat(result.getStatus()).isEqualTo(ObservationStatus.RECEIVED);
                    assertThat(result.getReceivedBy()).isEqualTo(10L);
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                })
                .verifyComplete();

        ArgumentCaptor<Observation> captor=
                ArgumentCaptor.forClass(Observation.class);

        verify(observationRepository).save(captor.capture());

        Observation saved=captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getObservationReference()).startsWith("OBS-");
        assertThat(saved.getMeasurementType()).isEqualTo(MeasurementType.RAINFALL);
        assertThat(saved.getMeasurementUnit()).isEqualTo(MeasurementUnit.MM);
        assertThat(saved.getSource()).isEqualTo(ObservationSource.MANUAL);
        assertThat(saved.getSourceReference()).isEqualTo("REF-001");
        assertThat(saved.getStatus()).isEqualTo(ObservationStatus.RECEIVED);
        assertThat(saved.getReceivedBy()).isEqualTo(10L);
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void create_whenSourceReferenceIsAbsent_shouldSkipDuplicateCheck() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                new BigDecimal("25.50")
        );

        LocationZone zone=zone(LocationZoneStatus.ACTIVE);

        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone),Mono.just(zone));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->{
                    Observation saved=invocation.getArgument(0);
                    saved.setId(1L);
                    return Mono.just(saved);
                });

        StepVerifier.create(observationService.create(request,10L))
                .assertNext(result->{
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getSourceReference()).isNull();
                    assertThat(result.getStatus()).isEqualTo(ObservationStatus.RECEIVED);
                })
                .verifyComplete();

        verify(observationRepository,never()).existsBySourceReference(any());
    }

    @Test
    void create_whenSourceReferenceAlreadyExists_shouldReturnConflict() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                new BigDecimal("25.50")
        );
        request.setSourceReference(" DUPLICATE ");

        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ACTIVE)));
        when(observationRepository.existsBySourceReference("DUPLICATE"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(observationService.create(request,10L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("An observation with this source reference already exists");
                })
                .verify();

        verify(observationRepository,never()).save(any());
    }

    @Test
    void create_whenZoneDoesNotExist_shouldReturnNotFound() {
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.empty());

        StepVerifier.create(observationService.create(
                        request("RAINFALL","MM","MANUAL",new BigDecimal("25.50")),
                        10L
                ))
                .expectError(NotFoundException.class)
                .verify();

        verify(observationRepository,never()).existsBySourceReference(any());
        verify(observationRepository,never()).save(any());
    }

    @Test
    void create_whenZoneIsArchived_shouldReturnNotFound() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.ARCHIVED)));

        StepVerifier.create(observationService.create(
                        request("RAINFALL","MM","MANUAL",new BigDecimal("25.50")),
                        10L
                ))
                .expectError(NotFoundException.class)
                .verify();

        verify(observationRepository,never()).save(any());
    }

    @Test
    void create_whenZoneIsInactive_shouldReturnConflict() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(LocationZoneStatus.INACTIVE)));

        StepVerifier.create(observationService.create(
                        request("RAINFALL","MM","MANUAL",new BigDecimal("25.50")),
                        10L
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo("Location zone must be ACTIVE");
                })
                .verify();

        verify(observationRepository,never()).save(any());
    }

    @Test
    void create_whenMeasurementUnitIsIncompatible_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "CELSIUS",
                "MANUAL",
                new BigDecimal("25.50")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenSourceIsInvalid_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "UNKNOWN",
                new BigDecimal("25.50")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Invalid observation source"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenObservedValueIsNull_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                null
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Observed value is required"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenRainfallIsNegative_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                new BigDecimal("-1.00")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("RAINFALL value cannot be negative"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenHumidityIsGreaterThanHundred_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "HUMIDITY",
                percentageUnit(),
                "MANUAL",
                new BigDecimal("101.00")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("HUMIDITY value must be between 0 and 100"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenTemperatureIsGreaterThanHundred_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "TEMPERATURE",
                "CELSIUS",
                "MANUAL",
                new BigDecimal("101.00")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Temperature must be between -100 and 100 Celsius"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenObservedAtIsNull_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                new BigDecimal("25.50")
        );
        request.setObservedAt(null);

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Observation time is required"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenObservationTimeIsFuture_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "MANUAL",
                new BigDecimal("25.50")
        );
        request.setObservedAt(LocalDateTime.now().plusHours(1));

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Observation time cannot be in the future"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenWeatherApiSourceReferenceIsAbsent_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "WEATHER_API",
                new BigDecimal("25.50")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Source reference is required for WEATHER_API observations"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    @Test
    void create_whenSensorSourceReferenceIsAbsent_shouldThrowValidationException() {
        CreateObservationRequest request=request(
                "RAINFALL",
                "MM",
                "SENSOR",
                new BigDecimal("25.50")
        );

        assertThatThrownBy(()->observationService.create(request,10L))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getErrors()).contains("Source reference is required for SENSOR observations"));

        verifyNoInteractions(observationRepository,locationZoneRepository);
    }

    private LocationZone zone(LocationZoneStatus status) {
        return LocationZone.builder()
                .id(1L)
                .zoneCode("ZONE_1")
                .zoneName("Zone 1")
                .country("US")
                .state("CT")
                .district("Hartford")
                .status(status)
                .build();
    }

    private Observation observation(Long id,String reference) {
        LocalDateTime now=LocalDateTime.now();

        return Observation.builder()
                .id(id)
                .observationReference(reference)
                .locationZoneId(1L)
                .measurementType(MeasurementType.RAINFALL)
                .measurementUnit(MeasurementUnit.MM)
                .observedValue(new BigDecimal("25.50"))
                .observedAt(now.minusHours(1))
                .source(ObservationSource.MANUAL)
                .status(ObservationStatus.VALIDATED)
                .receivedBy(1L)
                .receivedAt(now)
                .build();
    }

    private CreateObservationRequest request(String type,String unit,String source,
                                             BigDecimal value) {
        return CreateObservationRequest.builder()
                .locationZoneId(1L)
                .measurementType(type)
                .measurementUnit(unit)
                .observedValue(value)
                .observedAt(LocalDateTime.now().minusHours(1))
                .source(source)
                .build();
    }

    private String percentageUnit() {
        for(MeasurementUnit unit:MeasurementUnit.values()) {
            if(unit.name().equals("PERCENTAGE")) {
                return "PERCENTAGE";
            }

            if(unit.name().equals("PERCENT")) {
                return "PERCENT";
            }
        }

        return "PERCENTAGE";
    }
}