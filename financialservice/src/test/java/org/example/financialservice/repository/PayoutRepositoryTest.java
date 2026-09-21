package org.example.financialservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class PayoutRepositoryTest{
    @Autowired
    private PayoutRepository repository;

    @BeforeEach
    void setUp(){
        repository.deleteAll().block();
    }

    private Payout buildPayout(String ref,Long customerId,
                               Long claimId,
                               PayoutStatus status){
        return Payout.builder()
                .payoutReference(ref)
                .customerId(customerId)
                .claimId(claimId)
                .claimNumber("CLM-"+claimId)
                .policyId(7L)
                .amount(new BigDecimal("5000.00"))
                .status(status)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveAndFindById(){
        Payout payout=buildPayout("PO-001",5L,10L,
                PayoutStatus.PENDING);
        Payout saved=repository.save(payout).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(found->{
                    assertThat(found.getPayoutReference())
                            .isEqualTo("PO-001");
                    assertThat(found.getCustomerId()).isEqualTo(5L);
                    assertThat(found.getClaimId()).isEqualTo(10L);
                })
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdOrderByCreatedAtDesc(){
        repository.save(
                buildPayout("PO-A",5L,10L,PayoutStatus.PENDING))
                .block();
        repository.save(
                buildPayout("PO-B",5L,11L,PayoutStatus.SUCCESS))
                .block();
        repository.save(
                buildPayout("PO-C",99L,12L,PayoutStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findAllByCustomerIdOrderByCreatedAtDesc(5L)
        )
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId(){
        Payout saved=repository.save(
                buildPayout("PO-002",5L,10L,PayoutStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),5L)
        )
                .assertNext(q->assertThat(q.getPayoutReference())
                        .isEqualTo("PO-002"))
                .verifyComplete();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),99L)
        )
                .verifyComplete();
    }

    @Test
    void existsByClaimId(){
        repository.save(
                buildPayout("PO-003",5L,10L,PayoutStatus.PENDING))
                .block();

        StepVerifier.create(repository.existsByClaimId(10L))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(repository.existsByClaimId(99L))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void countByStatus(){
        repository.save(
                buildPayout("PO-004",5L,10L,PayoutStatus.PENDING))
                .block();
        repository.save(
                buildPayout("PO-005",5L,11L,PayoutStatus.SUCCESS))
                .block();

        StepVerifier.create(
                repository.countByStatus(PayoutStatus.PENDING)
        )
                .assertNext(count->assertThat(count).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void sumSuccessfulAmount(){
        repository.save(
                buildPayout("PO-006",5L,10L,PayoutStatus.SUCCESS))
                .block();
        repository.save(
                buildPayout("PO-007",5L,11L,PayoutStatus.SUCCESS))
                .block();
        repository.save(
                buildPayout("PO-008",5L,12L,PayoutStatus.PENDING))
                .block();

        StepVerifier.create(repository.sumSuccessfulAmount())
                .assertNext(sum->assertThat(sum)
                        .isEqualByComparingTo(
                                new BigDecimal("10000.00")))
                .verifyComplete();
    }

    @Test
    void findAllByClaimIdOrderByCreatedAtDesc(){
        repository.save(
                buildPayout("PO-009",5L,10L,PayoutStatus.PENDING))
                .block();
        repository.save(
                buildPayout("PO-010",6L,11L,PayoutStatus.SUCCESS))
                .block();
        repository.save(
                buildPayout("PO-011",5L,12L,PayoutStatus.PENDING))
                .block();

        StepVerifier.create(
                repository.findAllByClaimIdOrderByCreatedAtDesc(10L)
        )
                .assertNext(payout->assertThat(
                        payout.getPayoutReference())
                        .isEqualTo("PO-009"))
                .verifyComplete();
    }
}
