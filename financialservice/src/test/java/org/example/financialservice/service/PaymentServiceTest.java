package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.PaymentActionRequest;
import org.example.financialservice.dto.PaymentRequest;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.ForbiddenException;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentMethod;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.Quote;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PaymentServiceTest{
    private PaymentRepository paymentRepository;
    private QuoteRepository quoteRepository;
    private ProductServiceClient productServiceClient;
    private NotificationService notifications;
    private FinancialTransactionService financialTransactions;
    private PaymentService service;

    private static final InternalPolicyResponse POLICY=
            InternalPolicyResponse.builder()
                    .id(7L)
                    .policyNumber("POL-7")
                    .customerId(5L)
                    .productId(1L)
                    .status("ACTIVE")
                    .premiumAmount(new BigDecimal("10000.00"))
                    .build();

    private static final Quote CONSUMED_QUOTE=
            Quote.builder()
                    .id(1L)
                    .quoteReference("QTE-1")
                    .customerId(5L)
                    .productId(1L)
                    .locationZoneId(2L)
                    .sumInsured(new BigDecimal("100000.00"))
                    .termMonths(12)
                    .premiumAmount(new BigDecimal("10000.00"))
                    .status(QuoteStatus.CONSUMED)
                    .policyId(7L)
                    .policyNumber("POL-7")
                    .consumedAt(LocalDateTime.now())
                    .build();

    @BeforeEach
    void setUp(){
        paymentRepository=mock(PaymentRepository.class);
        quoteRepository=mock(QuoteRepository.class);
        productServiceClient=mock(ProductServiceClient.class);
        notifications=mock(NotificationService.class);
        financialTransactions=mock(FinancialTransactionService.class);

        when(notifications.audit(
                anyString(),
                anyString(),
                any(),
                any(),
                anyString()
        )).thenReturn(Mono.empty());
        when(financialTransactions.recordPayment(
                any(),any(),any(),any(),any(),anyString()
        )).thenReturn(Mono.empty());
        when(financialTransactions.recordRefund(
                any(),any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPaymentPending(
                anyLong(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPaymentRetry(
                anyLong(),any(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyPaymentRefunded(
                anyLong(),any(),any()
        )).thenReturn(Mono.empty());
        when(notifications.notifyRefundProcessed(
                anyLong(),any(),any()
        )).thenReturn(Mono.empty());

        service=new PaymentService(
                paymentRepository,
                quoteRepository,
                productServiceClient,
                notifications,
                financialTransactions
        );
    }

    private void stubPolicyFlow(){
        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(POLICY));
        when(quoteRepository.findByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Mono.just(CONSUMED_QUOTE));
        when(paymentRepository.existsByPolicyIdAndStatus(
                7L,
                PaymentStatus.SUCCESS
        )).thenReturn(Mono.just(false));
    }

    @Test
    void createDeniedWhenPolicyOwnedByOtherCustomer(){
        InternalPolicyResponse other=InternalPolicyResponse.builder()
                .id(7L)
                .policyNumber("POL-7")
                .customerId(99L)
                .premiumAmount(new BigDecimal("10000.00"))
                .build();

        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(other));

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_CARD)
                        .build()),
                5L,
                5L
        ))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    void createAllowsActivePolicyWithoutQuote(){
        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(POLICY));
        when(quoteRepository.findByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Mono.empty());
        when(paymentRepository.existsByPolicyIdAndStatus(
                7L,
                PaymentStatus.SUCCESS
        )).thenReturn(Mono.just(false));
        when(paymentRepository.countByPolicyId(7L))
                .thenReturn(Mono.just(0L));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->{
                    Payment payment=inv.getArgument(0);
                    payment.setId(9L);
                    return Mono.just(payment);
                });

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_CARD)
                        .build()),
                5L,
                5L
        ))
                .assertNext(response->{
                    assertThat(response.getQuoteId()).isNull();
                    assertThat(response.getAmount())
                            .isEqualByComparingTo("10000.00");
                })
                .verifyComplete();
    }

    @Test
    void createRejectsInactivePolicyWithoutQuote(){
        InternalPolicyResponse pending=InternalPolicyResponse.builder()
                .id(7L)
                .policyNumber("POL-7")
                .customerId(5L)
                .status("PENDING")
                .premiumAmount(new BigDecimal("10000.00"))
                .build();

        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(pending));
        when(quoteRepository.findByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_CARD)
                        .build()),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createPreventsDuplicatesOfSuccessfulPayment(){
        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(POLICY));
        when(quoteRepository.findByPolicyIdAndStatus(
                7L,
                QuoteStatus.CONSUMED
        )).thenReturn(Mono.just(CONSUMED_QUOTE));
        when(paymentRepository.existsByPolicyIdAndStatus(
                7L,
                PaymentStatus.SUCCESS
        )).thenReturn(Mono.just(true));
        when(paymentRepository.countByPolicyId(7L))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_CARD)
                        .build()),
                5L,
                5L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void createUsesPolicyPremiumAndAttemptNumber(){
        stubPolicyFlow();
        when(paymentRepository.countByPolicyId(7L))
                .thenReturn(Mono.just(2L));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->{
                    Payment payment=inv.getArgument(0);
                    payment.setId(9L);
                    return Mono.just(payment);
                });

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_UPI)
                        .build()),
                5L,
                5L
        ))
                .assertNext(response->{
                    assertThat(response.getAmount())
                            .isEqualByComparingTo(
                                    new BigDecimal("10000.00"));
                    assertThat(response.getPolicyNumber())
                            .isEqualTo("POL-7");
                    assertThat(response.getAttemptNumber())
                            .isEqualTo(3);
                    assertThat(response.getStatus())
                            .isEqualTo(PaymentStatus.PENDING);
                })
                .verifyComplete();
    }

    private Payment pendingPayment(Long id){
        return Payment.builder()
                .id(id)
                .paymentReference("PAY-1")
                .customerId(5L)
                .policyId(7L)
                .policyNumber("POL-7")
                .quoteId(1L)
                .amount(new BigDecimal("10000.00"))
                .method(PaymentMethod.MOCK_CARD)
                .status(PaymentStatus.PENDING)
                .attemptNumber(1)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void successTransition(){
        Payment payment=pendingPayment(1L);
        when(paymentRepository.findById(1L))
                .thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->{
                    Payment saved=inv.getArgument(0);
                    return Mono.just(saved);
                });
        when(notifications.notifyPaymentSuccess(
                anyLong(),
                any(),
                any()
        )).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.admin(
                service.success(
                        1L,
                        PaymentActionRequest.builder()
                                .externalReference("PSP-1")
                                .build()
                ),
                1L
        ))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo(PaymentStatus.SUCCESS))
                .verifyComplete();
    }

    @Test
    void successOnFailedPaymentRejected(){
        Payment payment=pendingPayment(1L);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setCompletedAt(LocalDateTime.now());

        when(paymentRepository.findById(1L))
                .thenReturn(Mono.just(payment));

        StepVerifier.create(TestAuth.admin(
                service.success(1L,new PaymentActionRequest()),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void failTransition(){
        Payment payment=pendingPayment(1L);
        when(paymentRepository.findById(1L))
                .thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));
        when(notifications.notifyPaymentFailed(
                anyLong(),
                any(),
                any()
        )).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.admin(
                service.fail(
                        1L,
                        PaymentActionRequest.builder()
                                .failureReason("Insufficient funds")
                                .build()
                ),
                1L
        ))
                .assertNext(response->{
                    assertThat(response.getStatus())
                            .isEqualTo(PaymentStatus.FAILED);
                    assertThat(response.getFailureReason())
                            .isEqualTo("Insufficient funds");
                })
                .verifyComplete();
    }

    @Test
    void refundTransition(){
        Payment payment=pendingPayment(1L);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setCompletedAt(LocalDateTime.now());

        when(paymentRepository.findById(1L))
                .thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        StepVerifier.create(TestAuth.admin(
                service.refund(1L),
                1L
        ))
                .assertNext(response->assertThat(response.getStatus())
                        .isEqualTo(PaymentStatus.REFUNDED))
                .verifyComplete();
    }

    @Test
    void refundOnlyFromSuccess(){
        // Failure is final for an attempt
        Payment payment=pendingPayment(1L);
        payment.setStatus(PaymentStatus.FAILED);

        when(paymentRepository.findById(1L))
                .thenReturn(Mono.just(payment));

        StepVerifier.create(TestAuth.admin(
                service.refund(1L),
                1L
        ))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void policyStatusInternal(){
        Payment successful=pendingPayment(1L);
        successful.setStatus(PaymentStatus.SUCCESS);
        successful.setCompletedAt(LocalDateTime.now());

        when(paymentRepository
                .findAllByPolicyIdOrderByCreatedAtDesc(7L))
                .thenReturn(Flux.just(successful));

        StepVerifier.create(service.policyStatus(7L))
                .assertNext(result->{
                    assertThat(result.isSuccessful()).isTrue();
                    assertThat(result.getLatestStatus())
                            .isEqualTo(PaymentStatus.SUCCESS);
                })
                .verifyComplete();
    }
}