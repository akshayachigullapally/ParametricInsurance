package org.example.productservice.service;

import org.example.productservice.dto.*;
import org.example.productservice.exception.*;
import org.example.productservice.model.*;
import org.example.productservice.repository.*;
import org.example.productservice.client.TriggerServiceClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.*;
import reactor.test.StepVerifier;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest{
    @Mock private PolicyRepository policyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PolicyTriggerSnapshotRepository snapshotRepository;
    @Mock private PolicyStatusHistoryRepository historyRepository;
    @Mock private NotificationService notificationService;
    @Mock private TriggerServiceClient triggerServiceClient;
    @Mock private org.example.productservice.client.FinancialServiceClient financialServiceClient;
    @Mock private ZoneUnderwritingService zoneUnderwritingService;
    @InjectMocks private PolicyService policyService;

    private Product product;
    private Policy pendingPolicy;

    @BeforeEach
    void setUp(){
        product=Product.builder()
                .id(10L)
                .productCode("WEATHER-01")
                .name("Weather Shield")
                .status(ProductStatus.ACTIVE.name())
                .minimumSumInsured(new BigDecimal("10000"))
                .maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(3)
                .maximumTermMonths(24)
                .build();

        pendingPolicy=Policy.builder()
                .id(100L)
                .policyNumber("POL-2026-ABC12345")
                .customerId(1L)
                .productId(10L)
                .productCode("WEATHER-01")
                .productName("Weather Shield")
                .locationZoneId(7L)
                .sumInsured(new BigDecimal("120000"))
                .premiumAmount(new BigDecimal("1200.00"))
                .termMonths(6)
                .status(PolicyStatus.PENDING.name())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // No pricing rule reachable: applications fall back to the temporary rate.
        lenient().when(financialServiceClient.calculatePremium(any(),any(),any(),any()))
                .thenReturn(Mono.empty());

        lenient().when(historyRepository.save(any(PolicyStatusHistory.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        lenient().when(notificationService.notifyPolicySubmitted(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyPendingReview(any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyApproved(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyRejected(any(),any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyCancelled(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(zoneUnderwritingService.checkCanSell(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.auditChange(any(),any(),any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.audit(any(),any(),any(),any(),any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void createPolicy_whenPendingApplicationExists_shouldReturnConflict(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(true));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "A pending policy application already exists for this product"
                    );
                })
                .verify();

        verify(policyRepository,never()).save(any(Policy.class));
    }

    @Test
    void createPolicy_whenSumInsuredIsBelowMinimum_shouldReturnValidationError(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"9000",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Sum insured cannot be less than 10000");
                })
                .verify();

        verifyNoInteractions(policyRepository);
    }

    @Test
    void createPolicy_whenSumInsuredExceedsMaximum_shouldReturnValidationError(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"500001",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Sum insured cannot exceed 500000");
                })
                .verify();
    }

    @Test
    void createPolicy_whenTermIsBelowMinimum_shouldReturnValidationError(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",2)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy term cannot be less than 3 months");
                })
                .verify();
    }

    @Test
    void createPolicy_whenTermExceedsMaximum_shouldReturnValidationError(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",25)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy term cannot exceed 24 months");
                })
                .verify();
    }

    @Test
    void getMyPolicies_whenStatusProvided_shouldReturnFilteredPolicies(){
        when(policyRepository.findAllByCustomerIdAndStatus(1L,"PENDING"))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getMyPolicies(1L,"pending"))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(100L);
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(policyRepository)
                .findAllByCustomerIdAndStatus(1L,"PENDING");
        verify(policyRepository,never()).findAllByCustomerId(anyLong());
    }

    @Test
    void getMyPolicies_whenStatusMissing_shouldReturnAllCustomerPolicies(){
        when(policyRepository.findAllByCustomerId(1L))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getMyPolicies(1L,null))
                .assertNext(response->
                        assertThat(response.getId()).isEqualTo(100L)
                )
                .verifyComplete();

        verify(policyRepository).findAllByCustomerId(1L);
        verify(policyRepository,never())
                .findAllByCustomerIdAndStatus(anyLong(),anyString());
    }

    @Test
    void getMyPolicies_whenStatusIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicies(1L,"UNKNOWN")
        );

        assertThat(exception.getMessage()).isEqualTo(
                "Status must be PENDING, ACTIVE, REJECTED, CANCELLED or EXPIRED"
        );
    }

    @Test
    void getMyPolicy_whenOwnedPolicyExists_shouldReturnIt(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.getMyPolicy(1L,100L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(100L);
                    assertThat(response.getPolicyNumber())
                            .isEqualTo("POL-2026-ABC12345");
                })
                .verifyComplete();
    }

    @Test
    void cancelPolicy_whenPending_shouldSaveCancelledPolicy(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(pendingPolicy));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->
                        Mono.just(invocation.getArgument(0))
                );

        StepVerifier.create(policyService.cancelPolicy(1L,100L))
                .assertNext(response->
                        assertThat(response.getMessage())
                                .isEqualTo("Policy cancelled successfully")
                )
                .verifyComplete();

        verify(policyRepository).save(argThat(policy->
                policy.getStatus().equals("CANCELLED")&&
                        policy.getUpdatedAt()!=null
        ));
    }

    @Test
    void getPendingPolicies_shouldReturnPendingPolicies(){
        when(policyRepository.findAllByStatus("PENDING"))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getPendingPolicies())
                .assertNext(response->
                        assertThat(response.getStatus()).isEqualTo("PENDING")
                )
                .verifyComplete();
    }

    @Test
    void getAllPolicies_whenStatusProvided_shouldFilterPolicies(){
        pendingPolicy.setStatus(PolicyStatus.ACTIVE.name());

        when(policyRepository.findAllByStatus("ACTIVE"))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getAllPolicies("active",null))
                .assertNext(response->
                        assertThat(response.getStatus()).isEqualTo("ACTIVE")
                )
                .verifyComplete();
    }

    @Test
    void getPolicy_whenFound_shouldReturnPolicy(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.getPolicy(100L))
                .assertNext(response->
                        assertThat(response.getId()).isEqualTo(100L)
                )
                .verifyComplete();
    }

    @Test
    void approvePolicy_whenPendingAndZoneAvailable_shouldActivatePolicy(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->
                        Mono.just(invocation.getArgument(0))
                );

        StepVerifier.create(policyService.approvePolicy(55L,100L))
                .assertNext(response->{
                    assertThat(response.getStatus()).isEqualTo("ACTIVE");
                    assertThat(response.getStartDate())
                            .isEqualTo(LocalDate.now());
                    assertThat(response.getEndDate())
                            .isEqualTo(LocalDate.now().plusMonths(6));
                    assertThat(response.getReviewedBy()).isEqualTo(55L);
                    assertThat(response.getReviewedAt()).isNotNull();
                    assertThat(response.getRejectionReason()).isNull();
                })
                .verifyComplete();

        verify(policyRepository).save(argThat(policy->
                policy.getStatus().equals("ACTIVE")&&
                        policy.getReviewedBy().equals(55L)&&
                        policy.getStartDate()!=null&&
                        policy.getEndDate()!=null
        ));
    }

    @Test
    void approvePolicy_whenReviewerIdInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.approvePolicy(0L,100L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Reviewer ID must be greater than zero");
    }

    @Test
    void rejectPolicy_whenPendingAndReasonValid_shouldRejectPolicy(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("  Risk too high  ")
                .build();

        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->
                        Mono.just(invocation.getArgument(0))
                );

        StepVerifier.create(policyService.rejectPolicy(55L,100L,request))
                .assertNext(response->{
                    assertThat(response.getStatus()).isEqualTo("REJECTED");
                    assertThat(response.getRejectionReason())
                            .isEqualTo("Risk too high");
                    assertThat(response.getReviewedBy()).isEqualTo(55L);
                    assertThat(response.getReviewedAt()).isNotNull();
                })
                .verifyComplete();

        verify(policyRepository).save(argThat(policy->
                policy.getStatus().equals("REJECTED")&&
                        policy.getRejectionReason().equals("Risk too high")&&
                        policy.getReviewedBy().equals(55L)
        ));
    }

    @Test
    void getInternalPolicy_whenFound_shouldMapInternalFields(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));
        when(snapshotRepository.findByPolicyId(100L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.getInternalPolicy(100L))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(100L);
                    assertThat(response.getPolicyNumber())
                            .isEqualTo("POL-2026-ABC12345");
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getLocationZoneId()).isEqualTo(7L);
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                })
                .verifyComplete();
    }

    private PolicyTriggerSnapshot productSnapshot(){
        return PolicyTriggerSnapshot.builder()
                .productId(10L)
                .productCode("WEATHER-01")
                .perilCode("EXCESS_RAINFALL")
                .measurementType("RAINFALL")
                .measurementUnit("mm")
                .thresholdValue(new BigDecimal("150.00"))
                .comparisonOperator("GREATER_THAN_OR_EQUAL")
                .aggregationType("SUM")
                .observationWindowHours(24)
                .minimumObservationCount(1)
                .triggerDescription(
                        "Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours."
                )
                .capturedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createPolicy_whenProductHasTriggerConfig_shouldCaptureProductSnapshot(){
        product.setPerilCode("EXCESS_RAINFALL");
        product.setMeasurementType("RAINFALL");
        product.setMeasurementUnit("mm");
        product.setThresholdValue(new BigDecimal("150.00"));
        product.setComparisonOperator("GREATER_THAN_OR_EQUAL");
        product.setAggregationType("SUM");
        product.setObservationWindowHours(24);
        product.setMinimumObservationCount(1);
        product.setTriggerDescription(
                "Coverage is triggered when total rainfall reaches or exceeds 150 mm within 24 hours."
        );

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(snapshotRepository.save(any(PolicyTriggerSnapshot.class)))
                .thenAnswer(invocation->{
                    PolicyTriggerSnapshot saved=invocation.getArgument(0);
                    saved.setId(30L);
                    return Mono.just(saved);
                });

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(100L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",6)
                ))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(100L);
                    assertThat(response.getTriggerSnapshotId()).isEqualTo(30L);
                })
                .verifyComplete();

        verify(snapshotRepository).save(argThat(snapshot->
                snapshot.getProductId().equals(10L)&&
                        snapshot.getProductCode().equals("WEATHER-01")&&
                        snapshot.getMeasurementType().equals("RAINFALL")&&
                        snapshot.getMeasurementUnit().equals("mm")&&
                        snapshot.getThresholdValue().compareTo(
                                new BigDecimal("150.00")
                        )==0&&
                        snapshot.getComparisonOperator()
                                .equals("GREATER_THAN_OR_EQUAL")&&
                        snapshot.getAggregationType().equals("SUM")&&
                        snapshot.getObservationWindowHours().equals(24)&&
                        snapshot.getMinimumObservationCount().equals(1)
        ));

        verify(policyRepository,times(2)).save(any(Policy.class));
    }

    @Test
    void createPolicy_whenNoProductTriggerButRequestHasTriggerData_shouldUseLegacySnapshot(){
        CreatePolicyRequest request=createRequest(10L,7L,"120000",6);
        request.setPerilCode("EXCESS_RAINFALL");
        request.setTriggerMetric("RAINFALL");
        request.setTriggerOperator("GREATER_THAN_OR_EQUAL");
        request.setTriggerThreshold(new BigDecimal("150.00"));
        request.setTriggerDurationHours(24);
        request.setTriggerAggregationType("SUM");
        request.setTriggerMinObservationCount(1);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(snapshotRepository.save(any(PolicyTriggerSnapshot.class)))
                .thenAnswer(invocation->{
                    PolicyTriggerSnapshot saved=invocation.getArgument(0);
                    saved.setId(55L);
                    return Mono.just(saved);
                });

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(100L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->
                        assertThat(response.getTriggerSnapshotId()).isEqualTo(55L)
                )
                .verifyComplete();

        verify(snapshotRepository).save(argThat(snapshot->
                snapshot.getProductId()==null&&
                        snapshot.getPerilCode().equals("EXCESS_RAINFALL")&&
                        snapshot.getTriggerMetric().equals("RAINFALL")&&
                        snapshot.getTriggerOperator()
                                .equals("GREATER_THAN_OR_EQUAL")&&
                        snapshot.getTriggerThreshold().compareTo(
                                new BigDecimal("150.00")
                        )==0
        ));

        verify(policyRepository,times(2)).save(any(Policy.class));
    }

    @Test
    void getPolicySnapshot_whenProductSnapshotExists_shouldReturnTriggerFields(){
        PolicyTriggerSnapshot snapshot=productSnapshot();
        snapshot.setId(30L);
        snapshot.setPolicyId(100L);

        when(policyRepository.existsById(100L))
                .thenReturn(Mono.just(true));

        when(snapshotRepository.findByPolicyId(100L))
                .thenReturn(Mono.just(snapshot));

        StepVerifier.create(policyService.getPolicySnapshot(100L))
                .assertNext(response->{
                    assertThat(response.getProductCode()).isEqualTo("WEATHER-01");
                    assertThat(response.getMeasurementType()).isEqualTo("RAINFALL");
                    assertThat(response.getMeasurementUnit()).isEqualTo("mm");
                    assertThat(response.getThresholdValue())
                            .isEqualByComparingTo(new BigDecimal("150.00"));
                    assertThat(response.getHumanReadableTrigger())
                            .isEqualTo("Total rainfall >= 150 mm within 24 hours");
                    assertThat(response.getCapturedAt()).isNotNull();
                })
                .verifyComplete();
    }

    private CreatePolicyRequest createRequest(
            Long productId,
            Long locationZoneId,
            String sumInsured,
            Integer termMonths
    ){
        return CreatePolicyRequest.builder()
                .productId(productId)
                .locationZoneId(locationZoneId)
                .sumInsured(
                        sumInsured==null?null:new BigDecimal(sumInsured)
                )
                .termMonths(termMonths)
                .build();
    }

    @Test
    void getReviewHistory_returnsOnlyApproveAndRejectDecisions(){
        LocalDateTime now=LocalDateTime.now();
        PolicyStatusHistory approved=PolicyStatusHistory.builder()
                .id(1L).policyId(100L).oldStatus("PENDING").newStatus("ACTIVE")
                .changedBy(50L).changedAt(now).build();
        PolicyStatusHistory rejected=PolicyStatusHistory.builder()
                .id(2L).policyId(100L).oldStatus("PENDING").newStatus("REJECTED")
                .changedBy(50L).changeReason("Outside risk appetite").changedAt(now.minusDays(1)).build();
        // A customer cancellation whose customer id collides with the reviewer's user id
        PolicyStatusHistory cancelled=PolicyStatusHistory.builder()
                .id(3L).policyId(100L).oldStatus("PENDING").newStatus("CANCELLED")
                .changedBy(50L).changedAt(now.minusDays(2)).build();

        when(historyRepository.findAllByChangedByAndOldStatusOrderByChangedAtDesc(50L,"PENDING"))
                .thenReturn(Flux.just(approved,rejected,cancelled));
        when(policyRepository.findById(100L)).thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.getReviewHistory(50L))
                .assertNext(r->{
                    assertThat(r.getDecision()).isEqualTo("APPROVED");
                    assertThat(r.getPolicyNumber()).isEqualTo("POL-2026-ABC12345");
                    assertThat(r.getProductName()).isEqualTo("Weather Shield");
                })
                .assertNext(r->{
                    assertThat(r.getDecision()).isEqualTo("REJECTED");
                    assertThat(r.getReason()).isEqualTo("Outside risk appetite");
                })
                .verifyComplete();
    }
}
