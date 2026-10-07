package org.example.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.productservice.client.FinancialServiceClient;
import org.example.productservice.client.TriggerServiceClient;
import org.example.productservice.dto.CreatePolicyRequest;
import org.example.productservice.dto.InternalQuoteResponse;
import org.example.productservice.exception.ConflictException;
import org.example.productservice.model.Policy;
import org.example.productservice.model.PolicyStatus;
import org.example.productservice.model.PolicyStatusHistory;
import org.example.productservice.model.Product;
import org.example.productservice.model.ProductStatus;
import org.example.productservice.repository.PolicyRepository;
import org.example.productservice.repository.PolicyStatusHistoryRepository;
import org.example.productservice.repository.PolicyTriggerSnapshotRepository;
import org.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PolicyRenewalTest{
    @Mock private PolicyRepository policyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PolicyTriggerSnapshotRepository snapshotRepository;
    @Mock private PolicyStatusHistoryRepository historyRepository;
    @Mock private NotificationService notificationService;
    @Mock private TriggerServiceClient triggerServiceClient;
    @Mock private FinancialServiceClient financialServiceClient;
    @Mock private ZoneUnderwritingService zoneUnderwritingService;
    @InjectMocks private PolicyService policyService;

    @BeforeEach
    void setUp(){
        Product product=Product.builder().id(10L).productCode("FLOOD-001").name("Flood Protection Plan")
                .status(ProductStatus.ACTIVE.name())
                .minimumSumInsured(new BigDecimal("10000")).maximumSumInsured(new BigDecimal("500000"))
                .minimumTermMonths(1).maximumTermMonths(12).build();

        lenient().when(productRepository.findByIdAndStatus(10L,"ACTIVE")).thenReturn(Mono.just(product));
        lenient().when(policyRepository.existsByCustomerIdAndProductIdAndStatus(any(),any(),any()))
                .thenReturn(Mono.just(false));
        lenient().when(policyRepository.save(any(Policy.class))).thenAnswer(invocation->{
            Policy policy=invocation.getArgument(0);
            if(policy.getId()==null){
                policy.setId(200L);
            }
            return Mono.just(policy);
        });
        lenient().when(policyRepository.findAllByRenewedFromPolicyId(any())).thenReturn(Flux.empty());
        lenient().when(historyRepository.save(any(PolicyStatusHistory.class)))
                .thenAnswer(invocation->Mono.just(invocation.getArgument(0)));
        lenient().when(notificationService.notifyPolicySubmitted(any(),any(),any())).thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyPendingReview(any(),any())).thenReturn(Mono.empty());
        lenient().when(notificationService.notifyPolicyApproved(any(),any(),any())).thenReturn(Mono.empty());
        lenient().when(zoneUnderwritingService.checkCanSell(any(),any(),any()))
                .thenReturn(Mono.empty());
        lenient().when(financialServiceClient.calculatePremium(any(),any(),any(),any()))
                .thenReturn(Mono.just(new BigDecimal("1875.00")));
    }

    private CreatePolicyRequest request(Long quoteId){
        return CreatePolicyRequest.builder().productId(10L).locationZoneId(4L)
                .sumInsured(new BigDecimal("100000")).termMonths(6).quoteId(quoteId).build();
    }

    private Policy active(LocalDate endDate){
        return Policy.builder().id(100L).policyNumber("POL-OLD").customerId(1L).productId(10L)
                .productCode("FLOOD-001").productName("Flood Protection Plan").locationZoneId(4L)
                .sumInsured(new BigDecimal("100000")).premiumAmount(new BigDecimal("1500.00"))
                .termMonths(6).status(PolicyStatus.ACTIVE.name())
                .startDate(endDate.minusMonths(6)).endDate(endDate)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void applicationUsesTheAcceptedQuotePremium(){
        when(financialServiceClient.validateQuote(any(),any())).thenReturn(Mono.just(
                InternalQuoteResponse.builder().valid(true).premiumAmount(new BigDecimal("2222.22")).build()));
        when(financialServiceClient.consumeQuote(any(),any()))
                .thenReturn(Mono.just(InternalQuoteResponse.builder().build()));

        StepVerifier.create(policyService.createPolicy(1L,request(55L)))
                .assertNext(policy->assertThat(policy.getPremiumAmount()).isEqualByComparingTo("2222.22"))
                .verifyComplete();
    }

    @Test
    void renewCreatesPendingApplicationLinkedToOldPolicy(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(active(LocalDate.now().plusDays(10))));

        StepVerifier.create(policyService.renewPolicy(1L,100L))
                .assertNext(renewal->{
                    assertThat(renewal.getStatus()).isEqualTo("PENDING");
                    assertThat(renewal.getRenewedFromPolicyId()).isEqualTo(100L);
                    assertThat(renewal.getPremiumAmount()).isEqualByComparingTo("1875.00");
                    assertThat(renewal.getSumInsured()).isEqualByComparingTo("100000");
                })
                .verifyComplete();
    }

    @Test
    void renewIsRefusedBeforeTheRenewalWindow(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(active(LocalDate.now().plusDays(90))));

        StepVerifier.create(policyService.renewPolicy(1L,100L))
                .expectErrorMatches(error->error instanceof ConflictException
                        &&error.getMessage().contains("Renewal opens"))
                .verify();
    }

    @Test
    void renewIsRefusedWhenAlreadyRenewed(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(active(LocalDate.now().plusDays(10))));
        when(policyRepository.findAllByRenewedFromPolicyId(100L)).thenReturn(Flux.just(
                Policy.builder().id(201L).status(PolicyStatus.PENDING.name()).build()));

        StepVerifier.create(policyService.renewPolicy(1L,100L))
                .expectErrorMatches(error->error.getMessage().contains("already been renewed"))
                .verify();
    }

    @Test
    void autoRenewCanBeToggledOnActivePolicy(){
        when(policyRepository.findByIdAndCustomerId(100L,1L))
                .thenReturn(Mono.just(active(LocalDate.now().plusDays(60))));

        StepVerifier.create(policyService.setAutoRenew(1L,100L,true))
                .assertNext(policy->assertThat(policy.getAutoRenew()).isTrue())
                .verifyComplete();
    }

    @Test
    void approvedRenewalStartsWhenThePreviousPolicyEnds(){
        LocalDate previousEnd=LocalDate.now().plusDays(10);
        Policy renewal=Policy.builder().id(200L).policyNumber("POL-NEW").customerId(1L).productId(10L)
                .locationZoneId(4L).sumInsured(new BigDecimal("100000")).premiumAmount(new BigDecimal("1875"))
                .termMonths(6).status(PolicyStatus.PENDING.name()).renewedFromPolicyId(100L)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(policyRepository.findById(200L)).thenReturn(Mono.just(renewal));
        when(policyRepository.findById(100L)).thenReturn(Mono.just(active(previousEnd)));

        StepVerifier.create(policyService.approvePolicy(9L,200L))
                .assertNext(approved->{
                    assertThat(approved.getStartDate()).isEqualTo(previousEnd);
                    assertThat(approved.getEndDate()).isEqualTo(previousEnd.plusMonths(6));
                })
                .verifyComplete();
    }
}
