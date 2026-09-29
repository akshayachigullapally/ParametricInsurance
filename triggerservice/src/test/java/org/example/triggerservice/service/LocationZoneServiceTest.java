package org.example.triggerservice.service;

import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.LocationZoneRequest;
import org.example.triggerservice.exception.ConflictException;
import org.example.triggerservice.exception.NotFoundException;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationZoneServiceTest {
    @Mock private LocationZoneRepository locationZoneRepository;
    @Mock private NotificationService notificationService;
    @Mock private ProductTriggerClient productTriggerClient;
    @InjectMocks private LocationZoneService locationZoneService;

    @BeforeEach
    void stubAudit(){
        lenient().when(notificationService.auditChange(any(),any(),any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.audit(any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void getAll_whenStateIsAbsent_shouldReturnNonArchivedZones() {
        when(locationZoneRepository.findAllByStatusNotOrderByZoneNameAsc(LocationZoneStatus.ARCHIVED))
                .thenReturn(Flux.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.getAll(null))
                .assertNext(result->{
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                    assertThat(result.getStatus()).isEqualTo(LocationZoneStatus.ACTIVE);
                })
                .verifyComplete();

        verify(locationZoneRepository)
                .findAllByStatusNotOrderByZoneNameAsc(LocationZoneStatus.ARCHIVED);
    }

    @Test
    void getAll_whenStateIsProvided_shouldReturnActiveStateZones() {
        when(locationZoneRepository.findAllByStateIgnoreCaseAndStatusOrderByZoneNameAsc(
                "CT",LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.getAll(" CT "))
                .assertNext(result->assertThat(result.getState()).isEqualTo("CT"))
                .verifyComplete();
    }

    @Test
    void getActive_shouldReturnActiveZones() {
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.getActive())
                .assertNext(result->assertThat(result.getStatus())
                        .isEqualTo(LocationZoneStatus.ACTIVE))
                .verifyComplete();
    }

    @Test
    void getActiveInternal_shouldReturnInternalResponses() {
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.getActiveInternal())
                .assertNext(result->{
                    assertThat(result.getZoneId()).isEqualTo(1L);
                    assertThat(result.getZoneCode()).isEqualTo("ZONE_1");
                    assertThat(result.getStatus()).isEqualTo("ACTIVE");
                })
                .verifyComplete();
    }

    @Test
    void getById_whenZoneExists_shouldReturnZone() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.getById(1L))
                .assertNext(result->assertThat(result.getId()).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void getById_whenZoneIsArchived_shouldReturnNotFound() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ARCHIVED)));

        StepVerifier.create(locationZoneService.getById(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void getById_whenIdIsInvalid_shouldReturnValidationError() {
        StepVerifier.create(locationZoneService.getById(0L))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(locationZoneRepository);
    }

    @Test
    void getInternalById_shouldIncludeArchivedZone() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ARCHIVED)));

        StepVerifier.create(locationZoneService.getInternalById(1L))
                .assertNext(result->assertThat(result.getStatus()).isEqualTo("ARCHIVED"))
                .verifyComplete();
    }

    @Test
    void create_shouldNormalizeAndSaveZone() {
        LocationZoneRequest request=request(" new zone "," New Zone ");
        request.setPostalCode(" 06103 ");

        when(locationZoneRepository.existsByZoneCodeIgnoreCase("NEW_ZONE"))
                .thenReturn(Mono.just(false));
        when(locationZoneRepository.save(any(LocationZone.class))).thenAnswer(invocation->{
            LocationZone saved=invocation.getArgument(0);
            saved.setId(1L);
            return Mono.just(saved);
        });

        StepVerifier.create(locationZoneService.create(request,15L))
                .assertNext(result->{
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getZoneCode()).isEqualTo("NEW_ZONE");
                    assertThat(result.getZoneName()).isEqualTo("New Zone");
                    assertThat(result.getPostalCode()).isEqualTo("06103");
                    assertThat(result.getStatus()).isEqualTo(LocationZoneStatus.ACTIVE);
                    assertThat(result.getCreatedBy()).isEqualTo(15L);
                })
                .verifyComplete();
    }

    @Test
    void create_whenCodeExists_shouldReturnConflict() {
        LocationZoneRequest request=request("duplicate zone","Duplicate Zone");

        when(locationZoneRepository.existsByZoneCodeIgnoreCase("DUPLICATE_ZONE"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(locationZoneService.create(request,1L))
                .expectError(ConflictException.class)
                .verify();

        verify(locationZoneRepository,never()).save(any());
    }

    @Test
    void create_whenRequestIsNull_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(()->locationZoneService.create(null,1L)))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(locationZoneRepository);
    }

    @Test
    void update_shouldNormalizeAndUpdateZone() {
        LocationZone existing=zone(1L,"OLD_ZONE","Old Zone",LocationZoneStatus.ACTIVE);
        LocationZoneRequest request=request(" new zone "," New Zone ");

        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(existing));
        when(locationZoneRepository.existsByZoneCodeAndIdNot("NEW_ZONE",1L))
                .thenReturn(Mono.just(false));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.update(1L,request,20L))
                .assertNext(result->{
                    assertThat(result.getZoneCode()).isEqualTo("NEW_ZONE");
                    assertThat(result.getZoneName()).isEqualTo("New Zone");
                    assertThat(result.getUpdatedBy()).isEqualTo(20L);
                })
                .verifyComplete();
    }

    @Test
    void update_whenCodeBelongsToAnotherZone_shouldReturnConflict() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"OLD","Old",LocationZoneStatus.ACTIVE)));
        when(locationZoneRepository.existsByZoneCodeAndIdNot("DUPLICATE",1L))
                .thenReturn(Mono.just(true));

        StepVerifier.create(locationZoneService.update(
                        1L,request("duplicate","Duplicate"),20L))
                .expectError(ConflictException.class)
                .verify();

        verify(locationZoneRepository,never()).save(any());
    }

    @Test
    void updateStatus_shouldUpdateStatus() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.updateStatus(1L," inactive ",9L))
                .assertNext(result->{
                    assertThat(result.getStatus()).isEqualTo(LocationZoneStatus.INACTIVE);
                    assertThat(result.getUpdatedBy()).isEqualTo(9L);
                })
                .verifyComplete();
    }

    @Test
    void updateStatus_whenStatusIsSame_shouldReturnConflict() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.updateStatus(1L,"ACTIVE",9L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void updateStatus_whenArchivedRequested_shouldReturnValidationError() {
        StepVerifier.create(locationZoneService.updateStatus(1L,"ARCHIVED",9L))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(locationZoneRepository);
    }

    @Test
    void updateStatus_whenStatusIsInvalid_shouldReturnValidationError() {
        StepVerifier.create(Mono.defer(
                        ()->locationZoneService.updateStatus(1L,"UNKNOWN",9L)))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(locationZoneRepository);
    }

    @Test
    void archive_shouldSaveArchivedZone() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));
        when(productTriggerClient.hasActivePoliciesForZone(1L)).thenReturn(Mono.just(false));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.archive(1L,9L)).verifyComplete();

        ArgumentCaptor<LocationZone> captor=ArgumentCaptor.forClass(LocationZone.class);
        verify(locationZoneRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(LocationZoneStatus.ARCHIVED);
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo(9L);
    }

    @Test
    void archive_whenZoneHasActivePolicies_shouldReturnConflict() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"ZONE_1","Zone 1",LocationZoneStatus.ACTIVE)));
        when(productTriggerClient.hasActivePoliciesForZone(1L)).thenReturn(Mono.just(true));

        StepVerifier.create(locationZoneService.archive(1L,9L))
                .expectError(ConflictException.class)
                .verify();

        verify(locationZoneRepository,never()).save(any(LocationZone.class));
    }

    @Test
    void updateAlert_raise_shouldPauseSalesAndAuditOffToOn() {
        LocationZone zone=zone(1L,"HYD","Hyderabad",LocationZoneStatus.ACTIVE);
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.updateAlert(1L,
                        org.example.triggerservice.dto.ZoneAlertRequest.builder()
                                .active(true).message(" Cyclone warning ").build(),9L))
                .assertNext(result->{
                    assertThat(result.getWeatherAlert()).isTrue();
                    assertThat(result.getAlertMessage()).isEqualTo("Cyclone warning");
                    assertThat(result.getAlertRaisedAt()).isNotNull();
                })
                .verifyComplete();

        verify(notificationService).auditChange("ZONE_ALERT_RAISED","LOCATION_ZONE",1L,9L,
                "Raised weather alert for Hyderabad (HYD)",
                "Weather alert: OFF",
                "Weather alert: ON (Cyclone warning)");
    }

    @Test
    void updateAlert_clear_shouldResumeSales() {
        LocationZone zone=zone(1L,"HYD","Hyderabad",LocationZoneStatus.ACTIVE);
        zone.setWeatherAlert(true);
        zone.setAlertMessage("Flood");
        zone.setAlertRaisedAt(LocalDateTime.now());
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(zone));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.updateAlert(1L,
                        org.example.triggerservice.dto.ZoneAlertRequest.builder().active(false).build(),9L))
                .assertNext(result->{
                    assertThat(result.getWeatherAlert()).isFalse();
                    assertThat(result.getAlertMessage()).isNull();
                    assertThat(result.getAlertRaisedAt()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void updateAlert_clearWhenNoneRaised_shouldConflict() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"HYD","Hyderabad",LocationZoneStatus.ACTIVE)));

        StepVerifier.create(locationZoneService.updateAlert(1L,
                        org.example.triggerservice.dto.ZoneAlertRequest.builder().active(false).build(),9L))
                .expectError(ConflictException.class)
                .verify();

        verify(locationZoneRepository,never()).save(any());
    }

    @Test
    void updateExposureCap_shouldStoreCapAndAuditBeforeAfter() {
        when(locationZoneRepository.findById(1L))
                .thenReturn(Mono.just(zone(1L,"HYD","Hyderabad",LocationZoneStatus.ACTIVE)));
        when(locationZoneRepository.save(any(LocationZone.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(locationZoneService.updateExposureCap(1L,new BigDecimal("50000000"),9L))
                .assertNext(result->assertThat(result.getMaxExposure()).isEqualByComparingTo("50000000"))
                .verifyComplete();

        verify(notificationService).auditChange("ZONE_EXPOSURE_CAP_CHANGED","LOCATION_ZONE",1L,9L,
                "Changed exposure cap of Hyderabad (HYD)",
                "Exposure cap: No cap",
                "Exposure cap: ₹50000000");
    }

    @Test
    void updateExposureCap_whenNotPositive_shouldFailValidation() {
        StepVerifier.create(locationZoneService.updateExposureCap(1L,BigDecimal.ZERO,9L))
                .expectError(ValidationException.class)
                .verify();
    }

    private LocationZone zone(Long id,String code,String name,LocationZoneStatus status) {
        LocalDateTime now=LocalDateTime.now();
        return LocationZone.builder().id(id).zoneCode(code).zoneName(name).country("US")
                .state("CT").district("Hartford").postalCode("06103")
                .latitude(new BigDecimal("41.7658")).longitude(new BigDecimal("-72.6734"))
                .radiusKm(new BigDecimal("50.00")).status(status).createdBy(1L)
                .createdAt(now).updatedBy(1L).updatedAt(now).build();
    }

    private LocationZoneRequest request(String code,String name) {
        return LocationZoneRequest.builder().zoneCode(code).zoneName(name).country(" US ")
                .state(" CT ").district(" Hartford ").postalCode("06103")
                .latitude(new BigDecimal("41.7658")).longitude(new BigDecimal("-72.6734"))
                .radiusKm(new BigDecimal("50.00")).build();
    }
}