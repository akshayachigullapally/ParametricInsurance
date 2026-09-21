package org.example.financialservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.financialservice.model.Quote;
import org.example.financialservice.model.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class QuoteRepositoryTest{
    @Autowired
    private QuoteRepository repository;

    @BeforeEach
    void setUp(){
        repository.deleteAll().block();
    }

    private Quote buildQuote(String ref,Long customerId,
                             QuoteStatus status,Long policyId){
        return Quote.builder()
                .quoteReference(ref)
                .customerId(customerId)
                .productId(1L)
                .locationZoneId(2L)
                .sumInsured(new BigDecimal("100000.00"))
                .termMonths(12)
                .annualRate(new BigDecimal("0.100000"))
                .termFactor(new BigDecimal("1.0000"))
                .locationFactor(new BigDecimal("1.0000"))
                .premiumAmount(new BigDecimal("10000.00"))
                .status(status)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .policyId(policyId)
                .build();
    }

    @Test
    void saveAndFindById(){
        Quote quote=buildQuote("QTE-001",5L,
                QuoteStatus.CREATED,null);
        Quote saved=repository.save(quote).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(found->{
                    assertThat(found.getQuoteReference())
                            .isEqualTo("QTE-001");
                    assertThat(found.getCustomerId()).isEqualTo(5L);
                    assertThat(found.getStatus())
                            .isEqualTo(QuoteStatus.CREATED);
                })
                .verifyComplete();
    }

    @Test
    void findAllByCustomerIdOrderByCreatedAtDesc(){
        repository.save(
                buildQuote("QTE-A",5L,QuoteStatus.CREATED,null))
                .block();
        repository.save(
                buildQuote("QTE-B",5L,QuoteStatus.ACCEPTED,null))
                .block();
        repository.save(
                buildQuote("QTE-C",99L,QuoteStatus.CREATED,null))
                .block();

        StepVerifier.create(
                repository.findAllByCustomerIdOrderByCreatedAtDesc(5L)
        )
                .assertNext(q->assertThat(q.getCustomerId())
                        .isEqualTo(5L))
                .assertNext(q->assertThat(q.getCustomerId())
                        .isEqualTo(5L))
                .verifyComplete();
    }

    @Test
    void findByIdAndCustomerId(){
        Quote saved=repository.save(
                buildQuote("QTE-002",5L,QuoteStatus.CREATED,null))
                .block();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),5L)
        )
                .assertNext(q->assertThat(q.getQuoteReference())
                        .isEqualTo("QTE-002"))
                .verifyComplete();

        StepVerifier.create(
                repository.findByIdAndCustomerId(saved.getId(),99L)
        )
                .verifyComplete();
    }

    @Test
    void findByQuoteReference(){
        repository.save(
                buildQuote("QTE-REF",5L,QuoteStatus.CREATED,null))
                .block();

        StepVerifier.create(repository.findByQuoteReference("QTE-REF"))
                .assertNext(q->assertThat(q.getCustomerId())
                        .isEqualTo(5L))
                .verifyComplete();

        StepVerifier.create(
                repository.findByQuoteReference("NONEXISTENT")
        )
                .verifyComplete();
    }

    @Test
    void existsByCustomerIdAndProductIdAndLocationZoneIdAndStatus(){
        repository.save(
                buildQuote("QTE-003",5L,QuoteStatus.CREATED,null))
                .block();

        StepVerifier.create(repository
                .existsByCustomerIdAndProductIdAndLocationZoneIdAndStatus(
                        5L,1L,2L,QuoteStatus.CREATED))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(repository
                .existsByCustomerIdAndProductIdAndLocationZoneIdAndStatus(
                        5L,1L,2L,QuoteStatus.CONSUMED))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void findByPolicyIdAndStatus(){
        repository.save(
                buildQuote("QTE-004",5L,
                        QuoteStatus.CONSUMED,7L))
                .block();

        StepVerifier.create(
                repository.findByPolicyIdAndStatus(
                        7L,QuoteStatus.CONSUMED)
        )
                .assertNext(q->assertThat(q.getPolicyId())
                        .isEqualTo(7L))
                .verifyComplete();

        StepVerifier.create(
                repository.findByPolicyIdAndStatus(
                        7L,QuoteStatus.CREATED)
        )
                .verifyComplete();
    }

    @Test
    void countByStatus(){
        repository.save(
                buildQuote("QTE-005",5L,QuoteStatus.CREATED,null))
                .block();
        repository.save(
                buildQuote("QTE-006",5L,QuoteStatus.CREATED,null))
                .block();
        repository.save(
                buildQuote("QTE-007",5L,QuoteStatus.ACCEPTED,null))
                .block();

        StepVerifier.create(
                repository.countByStatus(QuoteStatus.CREATED))
                .assertNext(count->assertThat(count).isEqualTo(2L))
                .verifyComplete();

        StepVerifier.create(
                repository.countByStatus(QuoteStatus.ACCEPTED))
                .assertNext(count->assertThat(count).isEqualTo(1L))
                .verifyComplete();
    }

    @Test
    void findAllByPolicyIdAndStatus(){
        repository.save(
                buildQuote("QTE-008",5L,
                        QuoteStatus.CONSUMED,7L))
                .block();
        repository.save(
                buildQuote("QTE-009",6L,
                        QuoteStatus.CONSUMED,7L))
                .block();
        repository.save(
                buildQuote("QTE-010",5L,
                        QuoteStatus.ACCEPTED,7L))
                .block();

        StepVerifier.create(
                repository.findAllByPolicyIdAndStatus(
                        7L,QuoteStatus.CONSUMED)
        )
                .expectNextCount(2)
                .verifyComplete();
    }
}
