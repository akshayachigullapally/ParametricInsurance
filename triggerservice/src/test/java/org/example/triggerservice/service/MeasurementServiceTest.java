package org.example.triggerservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.example.triggerservice.client.ClaimsServiceClient;
import org.example.triggerservice.client.ProductTriggerClient;
import org.example.triggerservice.dto.ActivePolicyForTriggerResponse;
import org.example.triggerservice.dto.ClaimCreationRequest;
import org.example.triggerservice.dto.ClaimCreationResponse;
import org.example.triggerservice.dto.CurrentMeasurementRequest;
import org.example.triggerservice.dto.InternalProductConfig;
import org.example.triggerservice.dto.ManualReadingRequest;
import org.example.triggerservice.exception.ValidationException;
import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationSource;
import org.example.triggerservice.model.ObservationStatus;
import org.example.triggerservice.model.CurrentMeasurement;
import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.repository.CurrentMeasurementRepository;
import org.example.triggerservice.repository.LocationZoneRepository;
import org.example.triggerservice.repository.ObservationRepository;
import org.example.triggerservice.repository.PerilRepository;
import org.example.triggerservice.repository.TriggerDefinitionRepository;
import org.example.triggerservice.repository.TriggerEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness=Strictness.LENIENT)
class MeasurementServiceTest{
    @Mock private ObservationRepository observationRepository;
    @Mock private LocationZoneRepository locationZoneRepository;
    @Mock private CurrentMeasurementRepository currentMeasurementRepository;
    @Mock private ProductTriggerClient productTriggerClient;
    @Mock private ClaimsServiceClient claimsServiceClient;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TriggerEventRepository triggerEventRepository;
    @Mock private NotificationService notificationService;
    @Mock private TriggerDefinitionRepository triggerDefinitionRepository;
    @Mock private PerilRepository perilRepository;

    @InjectMocks private MeasurementService service;

    private final CurrentMeasurementRequest request=CurrentMeasurementRequest.builder()
            .productId(1L)
            .locationZoneId(1L)
            .currentValue(new BigDecimal("170"))
            .idempotencyKey("KEY-1")
            .build();

    @BeforeEach
    void setUp(){
        when(idempotencyService.isDuplicate("KEY-1")).thenReturn(Mono.just(false));
        when(idempotencyService.record(anyString(),anyString(),any())).thenReturn(Mono.empty());
        when(productTriggerClient.getProduct(1L)).thenReturn(Mono.just(InternalProductConfig.builder()
                .productId(1L)
                .productCode("WEATHER-001")
                .status("ACTIVE")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal("150"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .observationWindowHours(24)
                .build()));
        when(locationZoneRepository.findById(1L)).thenReturn(Mono.just(LocationZone.builder()
                .id(1L)
                .status(LocationZoneStatus.ACTIVE)
                .build()));
        when(currentMeasurementRepository.findByProductIdAndLocationZoneId(1L,1L))
                .thenReturn(Mono.empty());
        when(currentMeasurementRepository.save(any(CurrentMeasurement.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(triggerDefinitionRepository.findByProductIdAndMeasurementTypeAndLocationZoneIdAndStatusNot(
                anyLong(),any(),anyLong(),any())).thenReturn(Mono.empty());
        when(perilRepository.findByPerilCodeIgnoreCase(any())).thenReturn(Mono.empty());
        when(triggerEventRepository.save(any(TriggerEvent.class))).thenAnswer(invocation->{
            TriggerEvent event=invocation.getArgument(0);
            event.setId(9L);
            return Mono.just(event);
        });
    }

    @Test
    void onlyPaidPoliciesGetClaims_andEventCarriesTheWindow(){
        when(productTriggerClient.getActivePolicies(1L,1L)).thenReturn(Flux.just(
                policy(100L,true),
                policy(200L,false)));
        when(claimsServiceClient.createClaimsFromTriggerEvent(any()))
                .thenReturn(Mono.just(ClaimCreationResponse.builder().claimsCreated(1).claimsFailed(0).build()));

        StepVerifier.create(service.processCurrentMeasurement(request,1L))
                .assertNext(result->{
                    assertThat(result.isMatched()).isTrue();
                    assertThat(result.getMatchedPolicyCount()).isEqualTo(1);
                    assertThat(result.getUnpaidPolicyCount()).isEqualTo(1);
                    assertThat(result.getClaimsCreatedCount()).isEqualTo(1);
                    assertThat(result.getMessage()).contains("premium is unpaid");
                })
                .verifyComplete();

        ArgumentCaptor<ClaimCreationRequest> claims=ArgumentCaptor.forClass(ClaimCreationRequest.class);
        verify(claimsServiceClient).createClaimsFromTriggerEvent(claims.capture());
        assertThat(claims.getValue().getEligiblePolicies())
                .extracting(ClaimCreationRequest.EligiblePolicyInfo::getPolicyId)
                .containsExactly(100L);
        assertThat(claims.getValue().getEventWindowStart())
                .isEqualTo(claims.getValue().getEventWindowEnd().minusHours(24));

        ArgumentCaptor<TriggerEvent> event=ArgumentCaptor.forClass(TriggerEvent.class);
        verify(triggerEventRepository,org.mockito.Mockito.atLeastOnce()).save(event.capture());
        assertThat(event.getValue().getWindowStart()).isNotNull();
        assertThat(event.getValue().getWindowEnd()).isNotNull();
    }

    @Test
    void unpaidOnly_createsNoEventAndExplainsWhy(){
        when(productTriggerClient.getActivePolicies(1L,1L)).thenReturn(Flux.just(policy(200L,false)));

        StepVerifier.create(service.processCurrentMeasurement(request,1L))
                .assertNext(result->{
                    assertThat(result.isMatched()).isFalse();
                    assertThat(result.getUnpaidPolicyCount()).isEqualTo(1);
                    assertThat(result.getTriggerEventId()).isNull();
                    assertThat(result.getMessage())
                            .startsWith("No automatic claim was created.")
                            .contains("premium is unpaid");
                })
                .verifyComplete();

        verify(triggerEventRepository,never()).save(any());
        verify(claimsServiceClient,never()).createClaimsFromTriggerEvent(any());
    }

    @Test
    void belowThreshold_isNotCountedAsUnpaid(){
        CurrentMeasurementRequest low=CurrentMeasurementRequest.builder()
                .productId(1L).locationZoneId(1L)
                .currentValue(new BigDecimal("120")).idempotencyKey("KEY-1").build();
        when(productTriggerClient.getActivePolicies(eq(1L),eq(1L))).thenReturn(Flux.just(policy(200L,false)));

        StepVerifier.create(service.processCurrentMeasurement(low,1L))
                .assertNext(result->{
                    assertThat(result.isMatched()).isFalse();
                    assertThat(result.getUnpaidPolicyCount()).isZero();
                    assertThat(result.getMessage())
                            .isEqualTo("The current measurement did not match any active policy trigger.");
                })
                .verifyComplete();
    }

    @Test
    void manualReading_isStoredAsValidatedManualObservationInEveryActiveZone(){
        when(observationRepository.findLatestValidated("RAINFALL")).thenReturn(Mono.just(
                Observation.builder().observedValue(new BigDecimal("0.11")).build()));
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(LocationZone.builder().id(1L).build(),LocationZone.builder().id(2L).build()));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.recordManualReading(
                        ManualReadingRequest.builder().measurementType("rainfall").value(new BigDecimal("160")).build(),
                        1L,30))
                .assertNext(result->{
                    assertThat(result.getZoneCount()).isEqualTo(2);
                    assertThat(result.getValue()).isEqualByComparingTo("160");
                    assertThat(result.getPreviousValue()).isEqualByComparingTo("0.11");
                    assertThat(result.getHoldMinutes()).isEqualTo(30);
                })
                .verifyComplete();

        ArgumentCaptor<Observation> saved=ArgumentCaptor.forClass(Observation.class);
        verify(observationRepository,org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(observation->{
            assertThat(observation.getMeasurementType()).isEqualTo(MeasurementType.RAINFALL);
            assertThat(observation.getSource()).isEqualTo(ObservationSource.MANUAL);
            assertThat(observation.getStatus()).isEqualTo(ObservationStatus.VALIDATED);
            assertThat(observation.getObservedValue()).isEqualByComparingTo("160");
        });
        assertThat(saved.getAllValues()).extracting(Observation::getLocationZoneId).containsExactlyInAnyOrder(1L,2L);
    }

    @Test
    void manualReading_triggersProductsLikeTheSimulator(){
        when(observationRepository.findLatestValidated("RAINFALL")).thenReturn(Mono.empty());
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(LocationZone.builder().id(1L).build()));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(idempotencyService.isDuplicate(anyString())).thenReturn(Mono.just(false));
        when(productTriggerClient.getActivePolicies(1L,1L)).thenReturn(Flux.just(policy(100L,true),policy(200L,false)));
        when(claimsServiceClient.createClaimsFromTriggerEvent(any()))
                .thenReturn(Mono.just(ClaimCreationResponse.builder().claimsCreated(1).claimsFailed(0).build()));
        // Product 2 measures something else, so it is skipped.
        when(productTriggerClient.getProduct(2L)).thenReturn(Mono.just(InternalProductConfig.builder()
                .productId(2L).status("ACTIVE").measurementType("HUMIDITY").build()));

        StepVerifier.create(service.recordManualReading(ManualReadingRequest.builder()
                        .measurementType("RAINFALL").value(new BigDecimal("160")).productIds(java.util.List.of(1L,2L)).build(),
                        1L,60))
                .assertNext(result->{
                    assertThat(result.getMatchedPolicyCount()).isEqualTo(1);
                    assertThat(result.getClaimsCreatedCount()).isEqualTo(1);
                    assertThat(result.getUnpaidPolicyCount()).isEqualTo(1);
                    assertThat(result.getTriggeredProducts()).containsExactly("WEATHER-001");
                    assertThat(result.getHoldMinutes()).isEqualTo(60);
                })
                .verifyComplete();

        verify(productTriggerClient,never()).getActivePolicies(eq(2L),anyLong());
    }

    @Test
    void manualReading_belowThreshold_createsNoClaims(){
        when(observationRepository.findLatestValidated("RAINFALL")).thenReturn(Mono.empty());
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(LocationZone.builder().id(1L).build()));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        when(idempotencyService.isDuplicate(anyString())).thenReturn(Mono.just(false));
        when(productTriggerClient.getActivePolicies(1L,1L)).thenReturn(Flux.just(policy(100L,true)));

        StepVerifier.create(service.recordManualReading(ManualReadingRequest.builder()
                        .measurementType("RAINFALL").value(new BigDecimal("40")).productIds(java.util.List.of(1L)).build(),
                        1L,60))
                .assertNext(result->{
                    assertThat(result.getMatchedPolicyCount()).isZero();
                    assertThat(result.getClaimsCreatedCount()).isZero();
                    assertThat(result.getTriggeredProducts()).isEmpty();
                })
                .verifyComplete();

        verify(claimsServiceClient,never()).createClaimsFromTriggerEvent(any());
    }

    @Test
    void manualReading_rejectsImpossibleValues(){
        StepVerifier.create(service.recordManualReading(
                        ManualReadingRequest.builder().measurementType("HUMIDITY").value(new BigDecimal("120")).build(),1L,30))
                .expectErrorSatisfies(error->assertThat(error)
                        .isInstanceOfSatisfying(ValidationException.class,validation->assertThat(validation.getErrors())
                                .containsExactly("A percentage cannot be more than 100")))
                .verify();
        StepVerifier.create(service.recordManualReading(
                        ManualReadingRequest.builder().measurementType("RAINFALL").value(new BigDecimal("-1")).build(),1L,30))
                .expectErrorSatisfies(error->assertThat(((ValidationException)error).getErrors())
                        .containsExactly("Value cannot be negative"))
                .verify();
        StepVerifier.create(service.recordManualReading(
                        ManualReadingRequest.builder().measurementType("SNOW").value(BigDecimal.ONE).build(),1L,30))
                .expectErrorSatisfies(error->assertThat(((ValidationException)error).getErrors())
                        .containsExactly("Unknown measurement type: SNOW"))
                .verify();
        verify(observationRepository,never()).save(any());
    }

    @Test
    void manualReading_allowsBelowZeroTemperatures(){
        when(observationRepository.findLatestValidated("TEMPERATURE")).thenReturn(Mono.empty());
        when(locationZoneRepository.findAllByStatusOrderByZoneNameAsc(LocationZoneStatus.ACTIVE))
                .thenReturn(Flux.just(LocationZone.builder().id(1L).build()));
        when(observationRepository.save(any(Observation.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.recordManualReading(
                        ManualReadingRequest.builder().measurementType("TEMPERATURE").value(new BigDecimal("-4")).build(),1L,30))
                .assertNext(result->assertThat(result.getPreviousValue()).isNull())
                .verifyComplete();
    }

    private ActivePolicyForTriggerResponse policy(Long id,boolean paid){
        ActivePolicyForTriggerResponse.TriggerSnapshot snapshot=new ActivePolicyForTriggerResponse.TriggerSnapshot();
        snapshot.setThresholdValue(new BigDecimal("150"));
        snapshot.setComparisonOperator("GREATER_THAN_OR_EQUAL");
        return ActivePolicyForTriggerResponse.builder()
                .policyId(id)
                .policyNumber("POL-"+id)
                .customerId(id)
                .sumInsured(new BigDecimal("100000"))
                .status("ACTIVE")
                .premiumPaid(paid)
                .triggerSnapshot(snapshot)
                .build();
    }
}
