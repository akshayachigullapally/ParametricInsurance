package org.example.financialservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentMethod;
import org.example.financialservice.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class PaymentRepositoryTest{
    @Autowired
    private PaymentRepository repository;

    @BeforeEach
    void setUp(){
        repository.deleteAll().block();
    }

    private Payment buildPayment(String ref,Long customerId,
                                 Long policyId,
                                 PaymentStatus status){
        return Payment.builder()
                .paymentReference(ref)
                .customerId(customerId)
                .policyId(policyId)
                .policyNumber("POL-"+policyId)
                .quoteId(1L)
                .amount(new BigDecimal("10000.00"))
                .method(PaymentMethod.MOCK_CARD)
                .status(status)
                .attemptNumber(1)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveAndFindById(){
        Payment payment=buildPayment("PAY-001",5L,7L,
                PaymentStatus.PENDING);
        Payment saved=repository.save(payment).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(found->{
                    assertThat(found.getPaymentReference())
                            .isEqualTo("PAY-001");
                    assertThat(found.getCustomerId()).isEqualTo(5L);
                })
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdOrderByCreatedAtDesc(){
        repository.save(
                buildPayment("PAY-A",5L,7L,PaymentStatus.PENDING))
                .block();
        repository.save(
                buildPayment("PAY-B",5L,8L,PaymentStatus.SUCCESS))
                .block();
        repository.save(
                buildPayment("PAY-C",99L,9L,PaymentStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findAllByCustomerIdOrderByCreatedAtDesc(5L)
        )
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId(){
        Payment saved=repository.save(
                buildPayment("PAY-002",5L,7L,PaymentStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),5L)
        )
                .assertNext(q->assertThat(q.getPaymentReference())
                        .isEqualTo("PAY-002"))
                .verifyComplete();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),99L)
        )
                .verifyComplete();
    }

    @Test
    void existsByPolicyIdAndStatus(){
        repository.save(
                buildPayment("PAY-003",5L,7L,PaymentStatus.SUCCESS))
                .block();

        StepVerifier.create(
                repository.existsByPolicyIdAndStatus(
                        7L,PaymentStatus.SUCCESS)
        )
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(
                repository.existsByPolicyIdAndStatus(
                        7L,PaymentStatus.PENDING)
        )
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void countByPolicyId(){
        repository.save(
                buildPayment("PAY-004",5L,7L,PaymentStatus.PENDING))
                .block();
        repository.save(
                buildPayment("PAY-005",5L,7L,PaymentStatus.FAILED))
                .block();

        StepVerifier.create(repository.countByPolicyId(7L))
                .assertNext(count->assertThat(count).isEqualTo(2L))
                .verifyComplete();
    }

    @Test
    void countByStatus(){
        repository.save(
                buildPayment("PAY-006",5L,7L,PaymentStatus.PENDING))
                .block();
        repository.save(
                buildPayment("PAY-007",5L,8L,PaymentStatus.SUCCESS))
                .block();

        StepVerifier.create(
                repository.countByStatus(PaymentStatus.PENDING)
        )
                .assertNext(count->assertThat(count).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void sumSuccessfulAmount(){
        repository.save(
                buildPayment("PAY-008",5L,7L,PaymentStatus.SUCCESS))
                .block();
        repository.save(
                buildPayment("PAY-009",5L,8L,PaymentStatus.SUCCESS))
                .block();
        repository.save(
                buildPayment("PAY-010",5L,9L,PaymentStatus.PENDING))
                .block();

        StepVerifier.create(repository.sumSuccessfulAmount())
                .assertNext(sum->assertThat(sum)
                        .isEqualByComparingTo(
                                new BigDecimal("20000.00")))
                .verifyComplete();
    }

    @Test
    void findAllByPolicyIdOrderByCreatedAtDesc(){
        repository.save(
                buildPayment("PAY-011",5L,7L,PaymentStatus.PENDING))
                .block();
        repository.save(
                buildPayment("PAY-012",5L,7L,PaymentStatus.FAILED))
                .block();
        repository.save(
                buildPayment("PAY-013",5L,8L,PaymentStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findAllByPolicyIdOrderByCreatedAtDesc(7L)
        )
                .expectNextCount(2)
                .verifyComplete();
    }
}
