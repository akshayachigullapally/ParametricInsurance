package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.dto.PaymentRequest;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentMethod;
import org.example.financialservice.model.PaymentStatus;
import org.example.financialservice.model.QuoteStatus;
import org.example.financialservice.repository.PaymentRepository;
import org.example.financialservice.repository.QuoteRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class NotificationResilienceTest{
    @Test
    void notificationFailureDoesNotReversePaymentSuccess(){
        PaymentRepository paymentRepository=
                mock(PaymentRepository.class);
        QuoteRepository quoteRepository=mock(QuoteRepository.class);
        ProductServiceClient productServiceClient=
                mock(ProductServiceClient.class);
        NotificationServiceClient notificationClient=
                mock(NotificationServiceClient.class);
        NotificationService notifications=
                new NotificationService(notificationClient);

        Payment payment=Payment.builder()
                .id(1L)
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

        when(productServiceClient.findPolicy(7L))
                .thenReturn(Mono.just(InternalPolicyResponse.builder()
                        .id(7L)
                        .policyNumber("POL-7")
                        .customerId(5L)
                        .status("ACTIVE")
                        .premiumAmount(new BigDecimal("10000.00"))
                        .build()));
        when(quoteRepository.findByPolicyIdAndStatus(7L,QuoteStatus.CONSUMED))
                .thenReturn(Mono.empty());
        when(paymentRepository.findAllByPolicyIdAndPolicyNumberOrderByCreatedAtDesc(7L,"POL-7"))
                .thenReturn(Flux.just(payment));

        // Every notification attempt fails
        when(notificationClient.sendNotification(any()))
                .thenReturn(Mono.error(
                        new RuntimeException("notification down")
                ));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv->Mono.just(inv.getArgument(0)));

        FinancialTransactionService financialTransactions=
                mock(FinancialTransactionService.class);
        when(financialTransactions.recordPayment(
                any(),any(),any(),any(),any(),anyString()
        )).thenReturn(Mono.empty());

        PaymentService service=new PaymentService(
                paymentRepository,
                quoteRepository,
                productServiceClient,
                notifications,
                financialTransactions
        );

        StepVerifier.create(TestAuth.customer(
                service.create(PaymentRequest.builder()
                        .policyId(7L)
                        .method(PaymentMethod.MOCK_CARD)
                        .build()),
                5L,
                5L
        ))
                .assertNext(response->{
                    // Financial state change is preserved
                    assertThat(response.getStatus())
                            .isEqualTo(PaymentStatus.SUCCESS);
                    assertThat(response.getExternalReference())
                            .startsWith("TXN-");
                })
                .verifyComplete();
    }
}