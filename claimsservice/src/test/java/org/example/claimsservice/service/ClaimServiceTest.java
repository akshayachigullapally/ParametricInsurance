package org.example.claimsservice.service;

import org.example.claimsservice.dto.ApproveClaimRequest;
import org.example.claimsservice.dto.CreateClaimRequest;
import org.example.claimsservice.dto.InternalPolicyResponse;
import org.example.claimsservice.dto.PolicyTriggerEligibilityResponse;
import org.example.claimsservice.dto.RejectClaimRequest;
import org.example.claimsservice.dto.SettleClaimRequest;
import org.example.claimsservice.exception.ConflictException;
import org.example.claimsservice.exception.DependentServiceException;
import org.example.claimsservice.exception.ForbiddenException;
import org.example.claimsservice.exception.NotFoundException;
import org.example.claimsservice.exception.ValidationException;
import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimStatus;
import org.example.claimsservice.model.ClaimTimeline;
import org.example.claimsservice.repository.ClaimRepository;
import org.example.claimsservice.repository.ClaimTimelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimTimelineRepository claimTimelineRepository;

    @Mock
    private TriggerEligibilityClient triggerEligibilityClient;

    @Mock
    private NotificationService notificationService;

    @Mock
    private FinancialServiceClient financialServiceClient;

    private ExchangeFunction exchangeFunction;
    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        exchangeFunction=mock(ExchangeFunction.class);

        WebClient.Builder webClientBuilder=WebClient.builder()
                .exchangeFunction(exchangeFunction);

        claimService=new ClaimService(
                claimRepository,
                claimTimelineRepository,
                webClientBuilder,
                triggerEligibilityClient,
                notificationService,
                financialServiceClient
        );

        when(notificationService.notifyClaimSubmitted(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimCancelled(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimUnderReview(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimApproved(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimRejected(anyLong(),anyLong(),any(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimSettled(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyManualClaimSubmitted(anyLong(),anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.notifyClaimPendingReview(anyLong(),any()))
                .thenReturn(Mono.empty());
        when(notificationService.audit(any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
        when(financialServiceClient.isPremiumPaid(anyLong(),any()))
                .thenReturn(Mono.just(true));

        ReflectionTestUtils.setField(
                claimService,
                "internalGatewayKey",
                "test-internal-key"
        );
    }

    @Test
    void createClaim_whenRequestIsValid_shouldCreateClaim() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        when(claimRepository
                .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                        1L,
                        100L,
                        500L,
                        ClaimStatus.CANCELLED
                ))
                .thenReturn(Mono.just(false));

        when(claimRepository.save(any(Claim.class)))
                .thenAnswer(invocation->{
                    Claim claim=invocation.getArgument(0);
                    claim.setId(1L);
                    return Mono.just(claim);
                });

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.createClaim(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getClaimNumber())
                            .startsWith("CLM-");
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getPolicyId()).isEqualTo(100L);
                    assertThat(response.getPolicyNumber())
                            .isEqualTo("POL-100");
                    assertThat(response.getTriggerEventId()).isEqualTo(500L);
                    assertThat(response.getIncidentType())
                            .isEqualTo("FLOOD");
                    assertThat(response.getIncidentLocation())
                            .isEqualTo("Hyderabad");
                    assertThat(response.getClaimedAmount())
                            .isEqualByComparingTo("50000");
                    assertThat(response.getStatus())
                            .isEqualTo("SUBMITTED");
                })
                .verifyComplete();

        ArgumentCaptor<Claim> captor=
                ArgumentCaptor.forClass(Claim.class);

        verify(claimRepository).save(captor.capture());

        Claim savedClaim=captor.getValue();

        assertThat(savedClaim.getCustomerId()).isEqualTo(1L);
        assertThat(savedClaim.getPolicyId()).isEqualTo(100L);
        assertThat(savedClaim.getTriggerEventId()).isEqualTo(500L);
        assertThat(savedClaim.getIncidentType()).isEqualTo("FLOOD");
        assertThat(savedClaim.getStatus()).isEqualTo("SUBMITTED");
    }

    @Test
    void createClaim_whenPolicyIdIsInvalid_shouldReturnValidationException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(0L);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy ID must be greater than zero");
                })
                .verify();

        verify(request).getPolicyId();
        verifyNoInteractions(claimRepository,triggerEligibilityClient);
        verify(exchangeFunction,never()).exchange(any());
    }

    @Test
    void createClaim_whenPolicyIdIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(null);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy ID must be greater than zero");
                })
                .verify();

        verifyNoInteractions(claimRepository,triggerEligibilityClient);
    }

    @Test
    void createClaim_whenPolicyIdIsNegative_shouldReturnValidationException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(-5L);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy ID must be greater than zero");
                })
                .verify();
    }

    @Test
    void createClaim_whenPolicyDoesNotExist_shouldReturnNotFoundException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(999L);

        mockEmptyPolicyResponse(HttpStatus.NOT_FOUND);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy not found");
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenPremiumIsUnpaid_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        mockPolicyResponse(policy());

        when(financialServiceClient.isPremiumPaid(eq(100L),any()))
                .thenReturn(Mono.just(false));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Premium for this policy has not been paid; pay it before filing a claim"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenPolicyBelongsToAnotherCustomer_shouldReturnForbiddenException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);
        when(request.getPolicyId()).thenReturn(100L);

        InternalPolicyResponse policy=policy();
        policy.setCustomerId(2L);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Policy does not belong to the authenticated customer"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenPolicyIsInactive_shouldReturnConflictException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);
        when(request.getPolicyId()).thenReturn(100L);

        InternalPolicyResponse policy=policy();
        policy.setStatus("INACTIVE");

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claims can be submitted only for active policies"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenAmountExceedsSumInsured_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getClaimedAmount())
                .thenReturn(new BigDecimal("200000"));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claimed amount cannot exceed policy sum insured"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenTriggerIsNotEligible_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setEligible(false);
        eligibility.setEligibilityReason(
                "Rainfall threshold not reached"
        );

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claim is not eligible: Rainfall threshold not reached"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenIncidentIsOutsideTriggerWindow_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setEventWindowStart(
                LocalDate.now().minusDays(10).atStartOfDay()
        );

        eligibility.setEventWindowEnd(
                LocalDate.now().minusDays(5).atTime(23,59)
        );

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident date must fall within the verified trigger event period"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenDuplicateExists_shouldReturnConflictException() {
        CreateClaimRequest request=createRequest();

        mockPolicyResponse(policy());

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility()));

        when(claimRepository
                .existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        any(ClaimStatus.class)
                ))
                .thenReturn(Mono.just(true));

        when(claimRepository.save(any(Claim.class)))
                .thenReturn(Mono.just(
                        Claim.builder().id(1L).claimNumber("CLM-TEST").build()
                ));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "A claim already exists for this policy and trigger event"
                    );
                })
                .verify();
    }

    @Test
    void createManualClaim_whenValid_shouldSubmitDirectlyWithoutEligibilityCheck() {
        CreateClaimRequest request=manualRequest();
        InternalPolicyResponse policy=policy();

        policy.setLocationZoneId(800L);

        mockPolicyResponse(policy);

        when(claimRepository
                .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                        eq(1L),
                        eq(100L),
                        any(LocalDate.class),
                        eq(ClaimStatus.CANCELLED)
                ))
                .thenReturn(Mono.just(false));

        when(claimRepository.save(any(Claim.class)))
                .thenAnswer(invocation->{
                    Claim claim=invocation.getArgument(0);
                    claim.setId(11L);
                    return Mono.just(claim);
                });

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.createClaim(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(11L);
                    assertThat(response.getClaimNumber())
                            .startsWith("CLM-");
                    assertThat(response.getStatus())
                            .isEqualTo("SUBMITTED");
                    assertThat(response.getClaimSource())
                            .isEqualTo("MANUAL_EXCEPTION");
                    assertThat(response.getManualReviewRequired())
                            .isEqualTo(true);
                    assertThat(response.getTriggerDetectionStatus())
                            .isEqualTo("UNDER_INVESTIGATION");
                    assertThat(response.getEvidenceDescription())
                            .isEqualTo("Heavy rain damaged the roof");
                    assertThat(response.getCustomerConfirmedLoss())
                            .isEqualTo(true);
                    assertThat(response.getTriggerEventId())
                            .isNull();
                })
                .verifyComplete();

        verifyNoInteractions(triggerEligibilityClient);

        ArgumentCaptor<Claim> captor=
                ArgumentCaptor.forClass(Claim.class);

        verify(claimRepository).save(captor.capture());

        Claim saved=captor.getValue();

        assertThat(saved.getClaimSource())
                .isEqualTo("MANUAL_EXCEPTION");
        assertThat(saved.getManualReviewRequired())
                .isEqualTo(true);
        assertThat(saved.getTriggerDetectionStatus())
                .isEqualTo("UNDER_INVESTIGATION");
        assertThat(saved.getCreatedFromTriggerEvent())
                .isEqualTo(false);
        assertThat(saved.getTriggerEventId()).isNull();

        verify(notificationService).notifyManualClaimSubmitted(
                eq(1L),eq(11L),any()
        );
    }

    @Test
    void createManualClaim_whenCustomerNotConfirmedLoss_shouldReturnValidationException() {
        CreateClaimRequest request=manualRequest();

        when(request.getCustomerConfirmedLoss()).thenReturn(false);

        mockPolicyResponse(policy());

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Customer must confirm the actual loss occurred"
                    );
                })
                .verify();

        verifyNoInteractions(triggerEligibilityClient);
        verify(claimRepository,never()).save(any());
    }

    @Test
    void createManualClaim_whenEvidenceMissing_shouldReturnValidationException() {
        CreateClaimRequest request=manualRequest();

        when(request.getEvidenceDescription()).thenReturn("  ");

        mockPolicyResponse(policy());

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Evidence description is required for a manual exception claim"
                    );
                })
                .verify();

        verifyNoInteractions(triggerEligibilityClient);
        verify(claimRepository,never()).save(any());
    }

    @Test
    void createManualClaim_whenDuplicateExists_shouldReturnConflictException() {
        CreateClaimRequest request=manualRequest();

        mockPolicyResponse(policy());

        when(claimRepository
                .existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
                        eq(1L),
                        eq(100L),
                        any(LocalDate.class),
                        eq(ClaimStatus.CANCELLED)
                ))
                .thenReturn(Mono.just(true));

        when(claimRepository.save(any(Claim.class)))
                .thenReturn(Mono.just(
                        Claim.builder().id(1L).claimNumber("CLM-TEST").build()
                ));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "A manual claim already exists for this policy and incident date"
                    );
                })
                .verify();

        verifyNoInteractions(triggerEligibilityClient);
    }

    @Test
    void getManualClaims_shouldReturnManualQueueClaims() {
        Claim manual=claim(3L,ClaimStatus.SUBMITTED);
        manual.setClaimSource("MANUAL_EXCEPTION");
        manual.setManualReviewRequired(true);
        manual.setTriggerDetectionStatus("UNDER_INVESTIGATION");

        when(claimRepository.findAllByManualReviewRequiredTrue())
                .thenReturn(Flux.just(manual));

        StepVerifier.create(claimService.getManualClaims())
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(3L);
                    assertThat(response.getClaimSource())
                            .isEqualTo("MANUAL_EXCEPTION");
                    assertThat(response.getManualReviewRequired())
                            .isEqualTo(true);
                })
                .verifyComplete();
    }

    @Test
    void getManualClaim_whenExists_shouldReturnClaim() {
        Claim manual=claim(3L,ClaimStatus.SUBMITTED);
        manual.setClaimSource("MANUAL_EXCEPTION");
        manual.setManualReviewRequired(true);

        when(claimRepository.findByIdAndManualReviewRequiredTrue(3L))
                .thenReturn(Mono.just(manual));

        StepVerifier.create(claimService.getManualClaim(3L))
                .assertNext(response->
                        assertThat(response.getClaimSource())
                                .isEqualTo("MANUAL_EXCEPTION")
                )
                .verifyComplete();
    }

    @Test
    void getManualClaim_whenMissing_shouldReturnNotFoundException() {
        when(claimRepository.findByIdAndManualReviewRequiredTrue(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(claimService.getManualClaim(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Manual claim not found"
                    );
                })
                .verify();
    }

    @Test
    void getMyClaims_whenStatusProvided_shouldReturnFilteredClaims() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);

        when(claimRepository.findAllByCustomerIdAndStatus(
                1L,
                "SUBMITTED"
        )).thenReturn(Flux.just(claim));

        StepVerifier.create(
                        claimService.getMyClaims(1L,"submitted")
                )
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getStatus())
                            .isEqualTo("SUBMITTED");
                })
                .verifyComplete();

        verify(claimRepository)
                .findAllByCustomerIdAndStatus(1L,"SUBMITTED");
    }

    @Test
    void getMyClaims_whenStatusMissing_shouldReturnAllCustomerClaims() {
        Claim first=claim(1L,ClaimStatus.SUBMITTED);
        Claim second=claim(2L,ClaimStatus.APPROVED);

        when(claimRepository.findAllByCustomerId(1L))
                .thenReturn(Flux.just(first,second));

        StepVerifier.create(claimService.getMyClaims(1L,null))
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("SUBMITTED")
                )
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("APPROVED")
                )
                .verifyComplete();
    }

    @Test
    void getMyClaims_whenStatusIsInvalid_shouldReturnValidationException() {
        StepVerifier.create(Flux.defer(()->
                        claimService.getMyClaims(1L,"INVALID")
                ))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(claimRepository);
    }

    @Test
    void getMyClaim_whenClaimExists_shouldReturnClaim() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.getMyClaim(1L,1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getStatus())
                            .isEqualTo("SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void getMyClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        when(claimRepository.findByIdAndCustomerId(99L,1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(claimService.getMyClaim(1L,99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();
    }

    @Test
    void cancelClaim_whenClaimIsSubmitted_shouldCancelClaim() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.cancelClaim(1L,1L))
                .assertNext(response->
                        assertThat(response.getMessage())
                                .isEqualTo(
                                        "Claim cancelled successfully"
                                )
                )
                .verifyComplete();

        assertThat(claim.getStatus()).isEqualTo("CANCELLED");
        assertThat(claim.getUpdatedAt()).isNotNull();

        verify(claimRepository).save(claim);
    }

    @Test
    void cancelClaim_whenClaimIsNotSubmitted_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.cancelClaim(1L,1L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only submitted claims can be cancelled"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void startReview_whenClaimIsSubmitted_shouldMoveUnderReview() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(claimService.startReview(50L,1L))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo("UNDER_REVIEW");
                    assertThat(response.getReviewedBy())
                            .isEqualTo(50L);
                    assertThat(response.getReviewedAt())
                            .isNotNull();
                })
                .verifyComplete();

        assertThat(claim.getStatus())
                .isEqualTo("UNDER_REVIEW");
        assertThat(claim.getReviewedBy()).isEqualTo(50L);
    }

    @Test
    void startReview_whenClaimIsNotSubmitted_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.startReview(50L,1L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only submitted claims can be reviewed"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenRequestIsValid_shouldApproveClaim() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        ApproveClaimRequest request=
                mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount())
                .thenReturn(new BigDecimal("40000"));

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(
                        claimService.approveClaim(50L,1L,request)
                )
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo("APPROVED");
                    assertThat(response.getApprovedAmount())
                            .isEqualByComparingTo("40000");
                    assertThat(response.getReviewedBy())
                            .isEqualTo(50L);
                })
                .verifyComplete();

        assertThat(claim.getStatus()).isEqualTo("APPROVED");
        assertThat(claim.getApprovedAmount())
                .isEqualByComparingTo("40000");
        assertThat(claim.getRejectionReason()).isNull();

        verify(claimRepository).save(claim);
    }

    @Test
    void approveClaim_whenAmountExceedsClaimedAmount_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount())
                .thenReturn(new BigDecimal("60000"));
        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.approveClaim(50L,1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Approved amount cannot exceed claimed amount"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void rejectClaim_whenReasonIsValid_shouldRejectClaim() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        RejectClaimRequest request=mock(RejectClaimRequest.class);

        when(request.getReason())
                .thenReturn("  Invalid supporting documents  ");

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(
                        claimService.rejectClaim(50L,1L,request)
                )
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo("REJECTED");
                    assertThat(response.getRejectionReason())
                            .isEqualTo(
                                    "Invalid supporting documents"
                            );
                    assertThat(response.getReviewedBy())
                            .isEqualTo(50L);
                })
                .verifyComplete();

        assertThat(claim.getStatus()).isEqualTo("REJECTED");
        assertThat(claim.getApprovedAmount()).isNull();
        assertThat(claim.getRejectionReason())
                .isEqualTo("Invalid supporting documents");

        verify(claimRepository).save(claim);
    }

    @Test
    void rejectClaim_whenReasonIsBlank_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        RejectClaimRequest request=mock(RejectClaimRequest.class);

        when(request.getReason()).thenReturn(" ");

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                        claimService.rejectClaim(50L,1L,request)
                )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo(
                                    "Rejection reason is required"
                            );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void getAllClaims_whenStatusProvided_shouldReturnFilteredClaims() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);

        when(claimRepository.findAllByStatus("APPROVED"))
                .thenReturn(Flux.just(claim));

        StepVerifier.create(
                        claimService.getAllClaims("approved", null)
                )
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("APPROVED")
                )
                .verifyComplete();

        verify(claimRepository).findAllByStatus("APPROVED");
        verify(claimRepository,never()).findAll();
    }

    @Test
    void getAllClaims_whenStatusMissing_shouldReturnAllClaims() {
        Claim first=claim(1L,ClaimStatus.SUBMITTED);
        Claim second=claim(2L,ClaimStatus.REJECTED);

        when(claimRepository.findAll())
                .thenReturn(Flux.just(first,second));

        StepVerifier.create(claimService.getAllClaims(null, null))
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("SUBMITTED")
                )
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("REJECTED")
                )
                .verifyComplete();
    }

    @Test
    void createClaim_whenClaimedAmountIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getClaimedAmount()).thenReturn(null);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claimed amount is required"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenClaimedAmountIsZero_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getClaimedAmount())
                .thenReturn(new BigDecimal("0"));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claimed amount must be greater than zero"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenClaimedAmountIsNegative_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getClaimedAmount())
                .thenReturn(new BigDecimal("-1000"));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claimed amount must be greater than zero"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentDateIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentDate()).thenReturn(null);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident date is required"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentDateIsFuture_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentDate())
                .thenReturn(LocalDate.now().plusDays(5));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident date cannot be in the future"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentDateBeforePolicyStart_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentDate())
                .thenReturn(policy.getStartDate().minusDays(1));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident date cannot be before policy start date"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentDateAfterPolicyEnd_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        policy.setEndDate(LocalDate.now().minusDays(2));

        when(request.getIncidentDate())
                .thenReturn(LocalDate.now().minusDays(1));

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident date cannot be after policy end date"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentTypeIsBlank_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentType()).thenReturn("  ");

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident type is required"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentTypeIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentType()).thenReturn(null);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident type is required"
                    );
                })
                .verify();
    }

    @Test
    void createClaim_whenIncidentLocationIsBlank_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentLocation()).thenReturn("  ");

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident location is required"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenIncidentLocationIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getIncidentLocation()).thenReturn(null);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Incident location is required"
                    );
                })
                .verify();
    }

    @Test
    void createClaim_whenDescriptionIsBlank_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getDescription()).thenReturn("  ");

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claim description is required"
                    );
                })
                .verify();

        verifyNoInteractions(
                claimRepository,
                triggerEligibilityClient
        );
    }

    @Test
    void createClaim_whenDescriptionIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();

        when(request.getDescription()).thenReturn(null);

        mockPolicyResponse(policy);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claim description is required"
                    );
                })
                .verify();
    }

    @Test
    void createClaim_whenPolicyIdIsNull_shouldReturnNotFoundException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(null);

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy ID must be greater than zero");
                })
                .verify();
    }

    @Test
    void createClaim_whenPolicyServiceReturns4xx_shouldReturnDependentServiceException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(100L);

        ClientResponse response=ClientResponse
                .create(HttpStatus.BAD_REQUEST)
                .build();

        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(DependentServiceException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Internal policy validation failed"
                    );
                })
                .verify();
    }

    @Test
    void createClaim_whenPolicyServiceReturns5xx_shouldReturnDependentServiceException() {
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(100L);

        ClientResponse response=ClientResponse
                .create(HttpStatus.SERVICE_UNAVAILABLE)
                .build();

        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(response));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(DependentServiceException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Product service is currently unavailable"
                    );
                })
                .verify();
    }

    @Test
    void createClaim_whenTriggerEligibilityPolicyIdMismatch_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setPolicyId(999L);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Trigger eligibility does not match the selected policy"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEligibilityCustomerIdMismatch_shouldReturnForbiddenException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setCustomerId(2L);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ForbiddenException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Trigger eligibility does not belong to the authenticated customer"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEventReferenceIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setTriggerEventReference(null);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Verified trigger event information is unavailable"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEventReferenceIsBlank_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setTriggerEventReference("  ");

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Verified trigger event information is unavailable"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEventIdIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setTriggerEventId(null);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Verified trigger event information is unavailable"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEventWindowsAreNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setEventWindowStart(null);
        eligibility.setEventWindowEnd(null);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Trigger event coverage period is unavailable"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEligibilityReasonIsNull_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setEligible(false);
        eligibility.setEligibilityReason(null);

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claim is not eligible: No verified trigger event applies to this policy"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void createClaim_whenTriggerEligibilityReasonIsBlank_shouldReturnValidationException() {
        CreateClaimRequest request=createRequest();
        InternalPolicyResponse policy=policy();
        PolicyTriggerEligibilityResponse eligibility=eligibility();

        eligibility.setEligible(false);
        eligibility.setEligibilityReason("  ");

        mockPolicyResponse(policy);

        when(triggerEligibilityClient.getEligibility(100L))
                .thenReturn(Mono.just(eligibility));

        StepVerifier.create(claimService.createClaim(1L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Claim is not eligible: No verified trigger event applies to this policy"
                    );
                })
                .verify();
    }

    @Test
    void startReview_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        when(claimRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(claimService.startReview(50L,99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(claimRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                claimService.approveClaim(50L,99L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenClaimIsSubmitted_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount())
                .thenReturn(new BigDecimal("40000"));

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.approveClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only claims under review can be approved"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenApprovedAmountIsNull_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount()).thenReturn(null);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.approveClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Approved amount is required"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenApprovedAmountIsZero_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount())
                .thenReturn(new BigDecimal("0"));

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.approveClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Approved amount must be greater than zero"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void approveClaim_whenApprovedAmountIsNegative_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        ApproveClaimRequest request=mock(ApproveClaimRequest.class);

        when(request.getApprovedAmount())
                .thenReturn(new BigDecimal("-5000"));

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.approveClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Approved amount must be greater than zero"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void rejectClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        RejectClaimRequest request=mock(RejectClaimRequest.class);

        when(claimRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                claimService.rejectClaim(50L,99L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void rejectClaim_whenClaimIsSubmitted_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);
        RejectClaimRequest request=mock(RejectClaimRequest.class);

        when(request.getReason())
                .thenReturn("Invalid documents");

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.rejectClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only claims under review can be rejected"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void rejectClaim_whenReasonIsNull_shouldReturnValidationException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        RejectClaimRequest request=mock(RejectClaimRequest.class);

        when(request.getReason()).thenReturn(null);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.rejectClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Rejection reason is required"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void getClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        when(claimRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(claimService.getClaim(99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();
    }

    @Test
    void getClaim_whenClaimExists_shouldReturnClaim() {
        Claim claim=claim(1L,ClaimStatus.SUBMITTED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.getClaim(1L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(1L);
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getStatus())
                            .isEqualTo("SUBMITTED");
                })
                .verifyComplete();
    }

    @Test
    void getSubmittedClaims_shouldReturnSubmittedClaims() {
        Claim first=claim(1L,ClaimStatus.SUBMITTED);
        Claim second=claim(2L,ClaimStatus.SUBMITTED);

        when(claimRepository.findAllByStatus(
                ClaimStatus.SUBMITTED.name()
        )).thenReturn(Flux.just(first,second));

        StepVerifier.create(claimService.getSubmittedClaims())
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("SUBMITTED")
                )
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("SUBMITTED")
                )
                .verifyComplete();

        verify(claimRepository)
                .findAllByStatus(ClaimStatus.SUBMITTED.name());
    }

    @Test
    void getSubmittedClaims_whenNoneExist_shouldReturnEmptyFlux() {
        when(claimRepository.findAllByStatus(
                ClaimStatus.SUBMITTED.name()
        )).thenReturn(Flux.empty());

        StepVerifier.create(claimService.getSubmittedClaims())
                .verifyComplete();
    }

    @Test
    void getAllClaims_whenStatusIsInvalid_shouldReturnValidationException() {
        StepVerifier.create(Flux.defer(
                () -> claimService.getAllClaims("INVALID_STATUS", null)
        ))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Status must be SUBMITTED, UNDER_REVIEW, APPROVED, SETTLED, REJECTED or CANCELLED"
                    );
                })
                .verify();

        verifyNoInteractions(claimRepository);
    }

    @Test
    void getMyClaims_whenNoClaimsExist_shouldReturnEmptyFlux() {
        when(claimRepository.findAllByCustomerId(1L))
                .thenReturn(Flux.empty());

        StepVerifier.create(claimService.getMyClaims(1L,null))
                .verifyComplete();
    }

    @Test
    void getMyClaims_whenFilteredStatusHasNoClaims_shouldReturnEmptyFlux() {
        when(claimRepository.findAllByCustomerIdAndStatus(
                1L,
                "APPROVED"
        )).thenReturn(Flux.empty());

        StepVerifier.create(
                claimService.getMyClaims(1L,"approved")
        )
                .verifyComplete();

        verify(claimRepository)
                .findAllByCustomerIdAndStatus(1L,"APPROVED");
    }

    @Test
    void cancelClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        when(claimRepository.findByIdAndCustomerId(99L,1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(claimService.cancelClaim(1L,99L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void cancelClaim_whenClaimIsUnderReview_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.cancelClaim(1L,1L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only submitted claims can be cancelled"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void cancelClaim_whenClaimIsApproved_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.cancelClaim(1L,1L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only submitted claims can be cancelled"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void cancelClaim_whenClaimIsRejected_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.REJECTED);

        when(claimRepository.findByIdAndCustomerId(1L,1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.cancelClaim(1L,1L))
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only submitted claims can be cancelled"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void settleClaim_whenRequestIsValid_shouldSettleClaim() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);
        SettleClaimRequest request=new SettleClaimRequest("Payment processed");

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(
                        claimService.settleClaim(50L,1L,request)
                )
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo("SETTLED");
                })
                .verifyComplete();

        assertThat(claim.getStatus()).isEqualTo("SETTLED");

        verify(claimRepository).save(claim);
        verify(claimTimelineRepository).save(any(ClaimTimeline.class));
    }

    @Test
    void settleFromPayout_whenClaimIsApproved_shouldSettleAsFinancialService() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));
        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));
        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        StepVerifier.create(claimService.settleFromPayout(1L,"PO-1"))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo("SETTLED"))
                .verifyComplete();

        verify(claimTimelineRepository).save(argThat(timeline->
                "FINANCIAL_SERVICE".equals(timeline.getActionByRole())&&
                        timeline.getNotes().contains("PO-1")));
    }

    @Test
    void settleFromPayout_whenClaimIsAlreadySettled_shouldReturnItUnchanged() {
        Claim claim=claim(1L,ClaimStatus.SETTLED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(claimService.settleFromPayout(1L,"PO-1"))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo("SETTLED"))
                .verifyComplete();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void settleClaim_whenClaimIsNotApproved_shouldReturnConflictException() {
        Claim claim=claim(1L,ClaimStatus.UNDER_REVIEW);
        SettleClaimRequest request=new SettleClaimRequest("Payment processed");

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        StepVerifier.create(
                claimService.settleClaim(50L,1L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Only approved claims can be settled"
                    );
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void settleClaim_whenClaimDoesNotExist_shouldReturnNotFoundException() {
        SettleClaimRequest request=new SettleClaimRequest("Payment processed");

        when(claimRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(
                claimService.settleClaim(50L,99L,request)
        )
                .expectErrorSatisfies(error->{
                    assertThat(error)
                            .isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Claim not found");
                })
                .verify();

        verify(claimRepository,never()).save(any());
    }

    @Test
    void settleClaim_whenRequestIsNull_shouldSettleWithDefaultNotes() {
        Claim claim=claim(1L,ClaimStatus.APPROVED);

        when(claimRepository.findById(1L))
                .thenReturn(Mono.just(claim));

        when(claimRepository.save(claim))
                .thenReturn(Mono.just(claim));

        when(claimTimelineRepository.save(any(ClaimTimeline.class)))
                .thenAnswer(invocation->{
                    ClaimTimeline timeline=invocation.getArgument(0);
                    timeline.setId(1L);
                    return Mono.just(timeline);
                });

        StepVerifier.create(
                        claimService.settleClaim(50L,1L,null)
                )
                .assertNext(response->
                        assertThat(response.getStatus())
                                .isEqualTo("SETTLED")
                )
                .verifyComplete();

        verify(claimRepository).save(claim);
    }

    private void mockPolicyResponse(
            InternalPolicyResponse policy
    ) {
        ClientResponse response=ClientResponse
                .create(HttpStatus.OK)
                .header("Content-Type","application/json")
                .body(policyJson(policy))
                .build();

        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(response));
    }

    private void mockEmptyPolicyResponse(HttpStatus status) {
        ClientResponse response=ClientResponse
                .create(status)
                .build();

        when(exchangeFunction.exchange(any()))
                .thenReturn(Mono.just(response));
    }

    private String policyJson(
            InternalPolicyResponse policy
    ) {
        return """
                {
                  "id": %d,
                  "customerId": %d,
                  "policyNumber": "%s",
                  "productId": %d,
                  "status": "%s",
                  "sumInsured": %s,
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """.formatted(
                policy.getId(),
                policy.getCustomerId(),
                policy.getPolicyNumber(),
                policy.getProductId(),
                policy.getStatus(),
                policy.getSumInsured(),
                policy.getStartDate(),
                policy.getEndDate()
        );
    }

    private CreateClaimRequest createRequest() {
        CreateClaimRequest request=
                mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(100L);
        when(request.getClaimedAmount())
                .thenReturn(new BigDecimal("50000"));
        when(request.getIncidentDate())
                .thenReturn(LocalDate.now().minusDays(1));
        when(request.getIncidentType())
                .thenReturn("  flood  ");
        when(request.getIncidentLocation())
                .thenReturn("  Hyderabad  ");
        when(request.getDescription())
                .thenReturn(
                        "  Property damaged because of flooding  "
                );

        return request;
    }

    private CreateClaimRequest manualRequest(){
        CreateClaimRequest request=mock(CreateClaimRequest.class);

        when(request.getPolicyId()).thenReturn(100L);
        when(request.getClaimedAmount())
                .thenReturn(new BigDecimal("50000"));
        when(request.getIncidentDate())
                .thenReturn(LocalDate.now().minusDays(1));
        when(request.getIncidentType())
                .thenReturn("  FLOOD  ");
        when(request.getIncidentLocation())
                .thenReturn("  Hyderabad  ");
        when(request.getDescription())
                .thenReturn(
                        "  Property damaged because of flooding  "
                );
        when(request.getClaimSource())
                .thenReturn("MANUAL_EXCEPTION");
        when(request.getEvidenceDescription())
                .thenReturn("Heavy rain damaged the roof");
        when(request.getCustomerConfirmedLoss())
                .thenReturn(true);

        return request;
    }

    private InternalPolicyResponse policy() {
        InternalPolicyResponse policy=
                new InternalPolicyResponse();

        policy.setId(100L);
        policy.setCustomerId(1L);
        policy.setPolicyNumber("POL-100");
        policy.setProductId(20L);
        policy.setStatus("ACTIVE");
        policy.setSumInsured(new BigDecimal("100000"));
        policy.setStartDate(
                LocalDate.now().minusMonths(1)
        );
        policy.setEndDate(
                LocalDate.now().plusMonths(11)
        );

        return policy;
    }

    private PolicyTriggerEligibilityResponse eligibility() {
        PolicyTriggerEligibilityResponse eligibility=
                new PolicyTriggerEligibilityResponse();

        eligibility.setPolicyId(100L);
        eligibility.setCustomerId(1L);
        eligibility.setEligible(true);
        eligibility.setEligibilityReason(
                "Verified trigger event found"
        );
        eligibility.setTriggerEventId(500L);
        eligibility.setTriggerEventReference("TRG-500");
        eligibility.setTriggerDefinitionId(600L);
        eligibility.setPerilId(700L);
        eligibility.setLocationZoneId(800L);
        eligibility.setEventWindowStart(
                LocalDate.now().minusDays(2).atStartOfDay()
        );
        eligibility.setEventWindowEnd(
                LocalDate.now().atTime(23,59,59)
        );

        return eligibility;
    }

    private Claim claim(Long id,ClaimStatus status) {
        LocalDateTime now=LocalDateTime.now();

        return Claim.builder()
                .id(id)
                .claimNumber("CLM-2026-TEST000"+id)
                .customerId(1L)
                .policyId(100L)
                .policyNumber("POL-100")
                .productId(20L)
                .triggerEventId(500L)
                .triggerEventReference("TRG-500")
                .triggerDefinitionId(600L)
                .perilId(700L)
                .locationZoneId(800L)
                .incidentType("FLOOD")
                .incidentDate(
                        LocalDate.now().minusDays(1)
                )
                .incidentLocation("Hyderabad")
                .description(
                        "Property damaged because of flooding"
                )
                .claimedAmount(new BigDecimal("50000"))
                .status(status.name())
                .submittedAt(now.minusHours(1))
                .updatedAt(now.minusMinutes(30))
                .build();
    }
}