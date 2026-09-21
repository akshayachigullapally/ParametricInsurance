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

        lenient().when(historyRepository.save(any(PolicyStatusHistory.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));

        lenient().when(notificationService.notifyPolicySubmitted(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyApproved(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyRejected(any(),any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyCancelled(any(),any(),any()))
                .thenReturn(Mono.empty());
    }

    @Test
    void createPolicy_whenRequestIsValid_shouldSavePendingPolicy(){
        CreatePolicyRequest request=createRequest(10L,7L,"120000",6);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(100L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(100L);
                    assertThat(response.getCustomerId()).isEqualTo(1L);
                    assertThat(response.getProductId()).isEqualTo(10L);
                    assertThat(response.getProductCode()).isEqualTo("WEATHER-01");
                    assertThat(response.getProductName()).isEqualTo("Weather Shield");
                    assertThat(response.getLocationZoneId()).isEqualTo(7L);
                    assertThat(response.getStatus()).isEqualTo("PENDING");
                    assertThat(response.getPremiumAmount())
                            .isEqualByComparingTo("1200.00");
                    assertThat(response.getPolicyNumber()).startsWith("POL-");
                })
                .verifyComplete();

        verify(policyRepository).save(argThat(policy->
                policy.getCustomerId().equals(1L)&&
                        policy.getProductId().equals(10L)&&
                        policy.getLocationZoneId().equals(7L)&&
                        policy.getTermMonths().equals(6)&&
                        policy.getStatus().equals("PENDING")
        ));
    }

    @Test
    void createPolicy_whenActiveProductDoesNotExist_shouldReturnNotFound(){
        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Active product not found");
                })
                .verify();

        verifyNoInteractions(policyRepository);
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
    void createPolicy_whenRequestIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(1L,null)
        );

        assertThat(exception.getMessage()).isEqualTo("Policy request is required");
        verifyNoInteractions(productRepository,policyRepository);
    }

    @Test
    void createPolicy_whenCustomerIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        0L,
                        createRequest(10L,7L,"120000",6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Customer ID must be greater than zero");
    }

    @Test
    void createPolicy_whenProductIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(0L,7L,"120000",6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Product ID must be greater than zero");
    }

    @Test
    void createPolicy_whenLocationZoneIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,0L,"120000",6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Location zone ID must be greater than zero");
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
    void getMyPolicies_whenStatusBlank_shouldReturnAllCustomerPolicies(){
        when(policyRepository.findAllByCustomerId(1L))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getMyPolicies(1L," "))
                .expectNextCount(1)
                .verifyComplete();

        verify(policyRepository).findAllByCustomerId(1L);
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
    void getMyPolicy_whenPolicyDoesNotExist_shouldReturnNotFound(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.getMyPolicy(1L,100L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage()).isEqualTo("Policy not found");
                })
                .verify();
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
    void cancelPolicy_whenNotPending_shouldReturnConflict(){
        pendingPolicy.setStatus(PolicyStatus.ACTIVE.name());

        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.cancelPolicy(1L,100L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Only pending policies can be cancelled");
                })
                .verify();

        verify(policyRepository,never()).save(any(Policy.class));
    }

    @Test
    void cancelPolicy_whenPolicyMissing_shouldReturnNotFound(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.cancelPolicy(1L,100L))
                .expectError(NotFoundException.class)
                .verify();
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

        StepVerifier.create(policyService.getAllPolicies("active"))
                .assertNext(response->
                        assertThat(response.getStatus()).isEqualTo("ACTIVE")
                )
                .verifyComplete();
    }

    @Test
    void getAllPolicies_whenStatusMissing_shouldReturnAllPolicies(){
        when(policyRepository.findAll())
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getAllPolicies(null))
                .expectNextCount(1)
                .verifyComplete();

        verify(policyRepository).findAll();
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
    void getPolicy_whenMissing_shouldReturnNotFound(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.getPolicy(100L))
                .expectError(NotFoundException.class)
                .verify();
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
    void approvePolicy_whenPolicyIsNotPending_shouldReturnConflict(){
        pendingPolicy.setStatus(PolicyStatus.ACTIVE.name());

        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.approvePolicy(55L,100L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Only pending policies can be approved");
                })
                .verify();

        verify(policyRepository,never()).save(any(Policy.class));
    }

    @Test
    void approvePolicy_whenLocationZoneUnavailable_shouldReturnValidationError(){
        pendingPolicy.setLocationZoneId(null);

        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.approvePolicy(55L,100L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Policy location zone is unavailable");
                })
                .verify();

        verify(policyRepository,never()).save(any(Policy.class));
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
    void rejectPolicy_whenPolicyIsNotPending_shouldReturnConflict(){
        pendingPolicy.setStatus(PolicyStatus.ACTIVE.name());

        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("Risk too high")
                .build();

        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

        StepVerifier.create(policyService.rejectPolicy(55L,100L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ConflictException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Only pending policies can be rejected");
                })
                .verify();

        verify(policyRepository,never()).save(any(Policy.class));
    }

    @Test
    void rejectPolicy_whenReasonMissing_shouldReturnValidationError(){
        StepVerifier.create(policyService.rejectPolicy(55L,100L,null))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Rejection reason is required");
                })
                .verify();

        verifyNoInteractions(policyRepository);
    }

    @Test
    void rejectPolicy_whenReasonBlank_shouldReturnValidationError(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason(" ")
                .build();

        StepVerifier.create(policyService.rejectPolicy(55L,100L,request))
                .expectError(ValidationException.class)
                .verify();

        verifyNoInteractions(policyRepository);
    }

    @Test
    void rejectPolicy_whenReasonExceedsLimit_shouldReturnValidationError(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("x".repeat(501))
                .build();

        StepVerifier.create(policyService.rejectPolicy(55L,100L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage()).isEqualTo(
                            "Rejection reason cannot exceed 500 characters"
                    );
                })
                .verify();

        verifyNoInteractions(policyRepository);
    }

    @Test
    void getInternalPolicy_whenFound_shouldMapInternalFields(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.just(pendingPolicy));

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

    @Test
    void getInternalPolicy_whenMissing_shouldReturnNotFound(){
        when(policyRepository.findById(100L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.getInternalPolicy(100L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void createPolicy_whenSumInsuredIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,null,6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Sum insured is required");
    }

    @Test
    void createPolicy_whenSumInsuredIsZero_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"0",6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Sum insured must be greater than zero");
    }

    @Test
    void createPolicy_whenSumInsuredIsNegative_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"-5000",6)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Sum insured must be greater than zero");
    }

    @Test
    void createPolicy_whenTermMonthsIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",null)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy term is required");
    }

    @Test
    void createPolicy_whenTermMonthsIsZero_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",0)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy term must be greater than zero");
    }

    @Test
    void createPolicy_whenTermMonthsIsNegative_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",-1)
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy term must be greater than zero");
    }

    @Test
    void getMyPolicies_whenCustomerIdIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicies(null,null)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Customer ID must be greater than zero");
    }

    @Test
    void getMyPolicies_whenCustomerIdIsZero_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicies(0L,null)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Customer ID must be greater than zero");
    }

    @Test
    void getMyPolicy_whenCustomerIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicy(0L,100L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Customer ID must be greater than zero");
    }

    @Test
    void getMyPolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicy(1L,0L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void cancelPolicy_whenCustomerIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.cancelPolicy(0L,100L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Customer ID must be greater than zero");
    }

    @Test
    void cancelPolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.cancelPolicy(1L,0L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void getMyPolicies_whenStatusIsInvalidWithNullCustomerId_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getMyPolicies(1L,"UNKNOWN")
        );

        assertThat(exception.getMessage()).isEqualTo(
                "Status must be PENDING, ACTIVE, REJECTED, CANCELLED or EXPIRED"
        );
    }

    @Test
    void getPolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getPolicy(0L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void getPolicy_whenPolicyIdIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getPolicy(null)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void approvePolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.approvePolicy(55L,0L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void approvePolicy_whenPolicyNotFound_shouldReturnNotFound(){
        when(policyRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.approvePolicy(55L,99L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void approvePolicy_whenReviewerIdIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.approvePolicy(null,100L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Reviewer ID must be greater than zero");
    }

    @Test
    void rejectPolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.rejectPolicy(
                        55L,
                        0L,
                        PolicyDecisionRequest.builder().reason("Risk").build()
                )
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void rejectPolicy_whenPolicyNotFound_shouldReturnNotFound(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("Risk too high")
                .build();

        when(policyRepository.findById(99L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.rejectPolicy(55L,99L,request))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    void rejectPolicy_whenReviewerIdIsNull_shouldThrowValidationException(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("Risk too high")
                .build();

        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.rejectPolicy(null,100L,request)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Reviewer ID must be greater than zero");
    }

    @Test
    void rejectPolicy_whenReasonIsBlank_shouldReturnValidationError(){
        PolicyDecisionRequest request=PolicyDecisionRequest.builder()
                .reason("   ")
                .build();

        StepVerifier.create(policyService.rejectPolicy(55L,100L,request))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Rejection reason is required");
                })
                .verify();
    }

    @Test
    void getInternalPolicy_whenPolicyIdIsInvalid_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getInternalPolicy(0L)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void getInternalPolicy_whenPolicyIdIsNull_shouldThrowValidationException(){
        ValidationException exception=Assertions.assertThrows(
                ValidationException.class,
                ()->policyService.getInternalPolicy(null)
        );

        assertThat(exception.getMessage())
                .isEqualTo("Policy ID must be greater than zero");
    }

    @Test
    void createPolicy_whenProductSumInsuredLimitsAreNull_shouldReturnValidationError(){
        product.setMinimumSumInsured(null);
        product.setMaximumSumInsured(null);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Product sum insured limits are unavailable");
                })
                .verify();
    }

    @Test
    void createPolicy_whenProductTermLimitsAreNull_shouldReturnValidationError(){
        product.setMinimumTermMonths(null);
        product.setMaximumTermMonths(null);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        StepVerifier.create(policyService.createPolicy(
                        1L,
                        createRequest(10L,7L,"120000",6)
                ))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(ValidationException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Product policy term limits are unavailable");
                })
                .verify();
    }

    @Test
    void getMyPolicies_whenStatusIsBlankWithNullStatus_shouldReturnAllCustomerPolicies(){
        when(policyRepository.findAllByCustomerId(1L))
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getMyPolicies(1L,""))
                .expectNextCount(1)
                .verifyComplete();

        verify(policyRepository).findAllByCustomerId(1L);
    }

    @Test
    void getAllPolicies_whenStatusIsBlank_shouldReturnAllPolicies(){
        when(policyRepository.findAll())
                .thenReturn(Flux.just(pendingPolicy));

        StepVerifier.create(policyService.getAllPolicies("  "))
                .expectNextCount(1)
                .verifyComplete();

        verify(policyRepository).findAll();
    }

    @Test
    void createPolicy_whenSumInsuredEqualsMinimum_shouldSavePolicy(){
        CreatePolicyRequest request=createRequest(10L,7L,"10000",6);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(101L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(101L);
                    assertThat(response.getSumInsured())
                            .isEqualByComparingTo("10000");
                })
                .verifyComplete();
    }

    @Test
    void createPolicy_whenSumInsuredEqualsMaximum_shouldSavePolicy(){
        CreatePolicyRequest request=createRequest(10L,7L,"500000",6);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(102L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(102L);
                    assertThat(response.getSumInsured())
                            .isEqualByComparingTo("500000");
                })
                .verifyComplete();
    }

    @Test
    void createPolicy_whenTermEqualsMinimum_shouldSavePolicy(){
        CreatePolicyRequest request=createRequest(10L,7L,"120000",3);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(103L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(103L);
                    assertThat(response.getTermMonths()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    void createPolicy_whenTermEqualsMaximum_shouldSavePolicy(){
        CreatePolicyRequest request=createRequest(10L,7L,"120000",24);

        when(productRepository.findByIdAndStatus(10L,"ACTIVE"))
                .thenReturn(Mono.just(product));

        when(policyRepository.existsByCustomerIdAndProductIdAndStatus(
                1L,10L,"PENDING"
        )).thenReturn(Mono.just(false));

        when(policyRepository.save(any(Policy.class)))
                .thenAnswer(invocation->{
                    Policy savedPolicy=invocation.getArgument(0);
                    savedPolicy.setId(104L);
                    return Mono.just(savedPolicy);
                });

        StepVerifier.create(policyService.createPolicy(1L,request))
                .assertNext(response->{
                    assertThat(response.getId()).isEqualTo(104L);
                    assertThat(response.getTermMonths()).isEqualTo(24);
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

    @Test
    void getPolicySnapshot_whenSnapshotMissing_shouldReturnNotFound(){
        when(policyRepository.existsById(100L))
                .thenReturn(Mono.just(true));

        when(snapshotRepository.findByPolicyId(100L))
                .thenReturn(Mono.empty());

        StepVerifier.create(policyService.getPolicySnapshot(100L))
                .expectErrorSatisfies(error->{
                    assertThat(error).isInstanceOf(NotFoundException.class);
                    assertThat(error.getMessage())
                            .isEqualTo("Trigger snapshot not found for this policy");
                })
                .verify();
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
}