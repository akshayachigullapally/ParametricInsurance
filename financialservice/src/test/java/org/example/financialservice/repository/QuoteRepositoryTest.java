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
}
