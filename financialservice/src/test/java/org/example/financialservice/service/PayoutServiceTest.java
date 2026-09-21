package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.InternalClaimResponse;
import org.example.financialservice.dto.CreatePayoutRequest;
import org.example.financialservice.dto.PayoutActionRequest;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.ValidationException;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PayoutServiceTest{
    private PayoutRepository payoutRepository;
    private ClaimsServiceClient claimsServiceClient;
    private TriggerServiceClient triggerServiceClient;
    private NotificationService notifications;
    private FinancialTransactionService financialTransactions;
    private PayoutService service;

    private static final InternalClaimResponse APPROVED_CLAIM=
            InternalClaimResponse.builder()
                    .claimId(10L)
                    .claimNumber("CLM-10")
                    .customerId(5L)
                    .policyId(7L)
                    .policyNumber("POL-7")
                    .productId(1L)
                    .triggerEventId(3L)
                    .status("APPROVED")
                    .approvedAmount(new BigDecimal("5000.00"))
                    .build();

    @BeforeEach
    void setUp(){
        payoutRepository=mock(PayoutRepository.class);
        claimsServiceClient=mock(ClaimsServiceClient.class);
        triggerServiceClient=mock(TriggerServiceClient.class);
        notifications=mock(NotificationService.class);
        financialTransactions=mock(FinancialTransactionService.class);

        when(notifications.audit(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        )).thenReturn(Mono.empty());
        when(financialTransactions.recordPayout(
                any(),any(),any(),any(),any(),anyString()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPayoutPending(
                any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPayoutRetry(
                any(),any(),any(),any()
        )).thenReturn(Mono.empty());
        when(triggerServiceClient.markEventProcessed(
                any()
        )).thenReturn(Mono.empty());

        service=new PayoutService(
                payoutRepository,
                claimsServiceClient,
                triggerServiceClient,
                notifications,
                financialTransactions
        );
    }

    private void stubApprovedClaim(){
        when(claimsServiceClient.findClaim(10L))
                .thenReturn(Mono.just(APPROVED_CLAIM));
        when(payoutRepository.existsByClaimId(10L))
                .thenReturn(Mono.just(false));
    }

    @Test
    void createRejectsNonApprovedClaim(){
        InternalClaimResponse pending=InternalClaimResponse.builder()
                .claimId(10L)
                .customerId(5L)
                .policyId(7L)
                .status("PENDING_REVIEW")
                .approvedAmount(new BigDecimal("5000.00"))
                .build();

        when(claimsServiceClient.findClaim(10L))
                .thenReturn(Mono.just(pending));

        StepVerifier.create(service.create(
                CreatePayoutRequest.builder().claimId(10L).build()
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createRejectsNonPositiveAmount(){
        InternalClaimResponse bad=InternalClaimResponse.builder()
                .claimId(10L)
                .customerId(5L)
                .policyId(7L)
                .status("APPROVED")
                .approvedAmount(BigDecimal.ZERO)
                .build();

        when(claimsServiceClient.findClaim(10L))
                .thenReturn(Mono.just(bad));

        StepVerifier.create(service.create(
                CreatePayoutRequest.builder().claimId(10L).build()
        ))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void duplicatePayoutPrevented(){
        when(claimsServiceClient.findClaim(10L))
                .thenReturn(Mono.just(APPROVED_CLAIM));
        when(payoutRepository.existsByClaimId(10L))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.create(
                CreatePayoutRequest.builder().claimId(10L).build()
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createUsesClaimValuesNotCallerInput(){
        stubApprovedClaim();
        when(payoutRepository.save(any(Payout.class)))
                .thenAnswer(inv->{
                    Payout payout=inv.getArgument(0);
                    payout.setId(1L);
                    return Mono.just(payout);
                });

        StepVerifier.create(service.create(
                CreatePayoutRequest.builder().claimId(10L).build()
        ))
                .assertNext(response->{
                    assertThat(response.getAmount())
                            .isEqualByComparingTo(
                                    new BigDecimal("5000.00"));
                    assertThat(response.getCustomerId()).isEqualTo(5L);
                    assertThat(response.getPolicyId()).isEqualTo(7L);
                    assertThat(response.getClaimNumber())
                            .isEqualTo("CLM-10");
                    assertThat(response.getTriggerEventId())
                            .isEqualTo(3L);
                    assertThat(response.getStatus())
                            .isEqualTo(PayoutStatus.PENDING);
                    assertThat(response.getRetryCount()).isZero();
                })
                .verifyComplete();
    }

    private Payout pendingPayout(Long id){
        return Payout.builder()
                .id(id)
                .payoutReference("PO-1")
                .customerId(5L)
                .claimId(10L)
                .claimNumber("CLM-10")
                .policyId(7L)
                .triggerEventId(3L)
                .amount(new BigDecimal("5000.00"))
                .status(PayoutStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void processTransition(){
        Payout payout=pendingPayout(1L);
        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));
        when(payoutRepository.save(any(Payout.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.admin(
                service.process(
                        1L,
                        new PayoutActionRequest()
                ),
                1L
        ))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo(PayoutStatus.PROCESSING))
                .verifyComplete();
    }

    @Test
    void successTransition(){
        Payout payout=pendingPayout(1L);
        payout.setStatus(PayoutStatus.PROCESSING);
        payout.setProcessedAt(LocalDateTime.now());

        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));
        when(payoutRepository.save(any(Payout.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(notifications.notifyPayoutProcessed(
                any(),
                any(),
                any()
        )).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.admin(
                service.success(
                        1L,
                        PayoutActionRequest.builder()
                                .externalReference("BANK-1")
                                .build()
                ),
                1L
        ))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo(PayoutStatus.SUCCESS);
                    assertThat(response.getExternalReference())
                            .isEqualTo("BANK-1");
                })
                .verifyComplete();
    }

    @Test
    void successOnlyFromProcessing(){
        Payout payout=pendingPayout(1L);
        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));

        StepVerifier.create(TestAuth.admin(
                service.success(
                        1L,
                        new PayoutActionRequest()
                ),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void failTransition(){
        Payout payout=pendingPayout(1L);
        payout.setStatus(PayoutStatus.PROCESSING);

        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));
        when(payoutRepository.save(any(Payout.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(notifications.notifyPayoutFailed(
                any(),
                any(),
                any()
        )).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.admin(
                service.fail(
                        1L,
                        PayoutActionRequest.builder()
                                .failureReason("Bank declined")
                                .build()
                ),
                1L
        ))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo(PayoutStatus.FAILED);
                    assertThat(response.getFailureReason())
                            .isEqualTo("Bank declined");
                })
                .verifyComplete();
    }

    @Test
    void retryTransitionIncrementsCount(){
        Payout payout=pendingPayout(1L);
        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason("Bank declined");
        payout.setRetryCount(1);

        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));
        when(payoutRepository.save(any(Payout.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.admin(
                service.retry(
                        1L,
                        new PayoutActionRequest()
                ),
                1L
        ))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo(PayoutStatus.PROCESSING);
                    assertThat(response.getRetryCount()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void retryOnlyFromFailed(){
        Payout payout=pendingPayout(1L);
        when(payoutRepository.findById(1L))
                .thenReturn(Mono.just(payout));

        StepVerifier.create(TestAuth.admin(
                service.retry(
                        1L,
                        new PayoutActionRequest()
                ),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }
}