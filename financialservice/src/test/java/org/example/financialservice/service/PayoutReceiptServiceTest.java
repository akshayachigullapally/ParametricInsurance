package org.example.financialservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.example.financialservice.TestAuth;
import org.example.financialservice.dto.InternalPolicyResponse;
import org.example.financialservice.exception.ConflictException;
import org.example.financialservice.exception.NotFoundException;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.example.financialservice.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PayoutReceiptServiceTest{
    private PayoutRepository payouts;
    private ProductServiceClient products;
    private PayoutReceiptService service;

    @BeforeEach
    void setUp(){
        payouts=mock(PayoutRepository.class);
        products=mock(ProductServiceClient.class);
        service=new PayoutReceiptService(payouts,products);
    }

    private Payout payout(PayoutStatus status){
        return Payout.builder().id(5L).payoutReference("PAY-ABC").customerId(7L)
                .claimId(3L).claimNumber("CLM-1").policyId(9L).triggerEventId(11L)
                .amount(new BigDecimal("50000.00")).status(status)
                .externalReference("UTR123").processedAt(LocalDateTime.now()).build();
    }

    @Test
    void rendersPdfForCompletedPayoutOfTheCustomer(){
        when(payouts.findByIdAndCustomerId(5L,7L)).thenReturn(Mono.just(payout(PayoutStatus.SUCCESS)));
        when(products.findPolicy(9L)).thenReturn(Mono.just(InternalPolicyResponse.builder()
                .policyNumber("POL-1").productName("Flood Protection Plan")
                .sumInsured(new BigDecimal("50000")).build()));

        StepVerifier.create(TestAuth.customer(service.receiptForCustomer(5L),7L,7L))
                .assertNext(receipt->{
                    assertThat(receipt.fileName()).isEqualTo("PAY-ABC-receipt.pdf");
                    assertThat(new String(receipt.bytes(),0,5,StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                })
                .verifyComplete();
    }

    @Test
    void stillRendersWhenPolicyLookupFails(){
        when(payouts.findByIdAndCustomerId(5L,7L)).thenReturn(Mono.just(payout(PayoutStatus.SUCCESS)));
        when(products.findPolicy(9L)).thenReturn(Mono.error(new RuntimeException("down")));

        StepVerifier.create(TestAuth.customer(service.receiptForCustomer(5L),7L,7L))
                .assertNext(receipt->assertThat(receipt.bytes().length).isGreaterThan(500))
                .verifyComplete();
    }

    @Test
    void refusesReceiptBeforePayoutCompletes(){
        when(payouts.findByIdAndCustomerId(5L,7L)).thenReturn(Mono.just(payout(PayoutStatus.PENDING)));

        StepVerifier.create(TestAuth.customer(service.receiptForCustomer(5L),7L,7L))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    void hidesOtherCustomersPayouts(){
        when(payouts.findByIdAndCustomerId(5L,8L)).thenReturn(Mono.empty());

        StepVerifier.create(TestAuth.customer(service.receiptForCustomer(5L),8L,8L))
                .expectError(NotFoundException.class)
                .verify();
    }
}
