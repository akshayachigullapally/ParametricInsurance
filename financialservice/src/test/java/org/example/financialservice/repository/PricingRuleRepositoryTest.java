package org.example.financialservice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class PricingRuleRepositoryTest{
    @Autowired
    private PricingRuleRepository repository;

    @BeforeEach
    void setUp(){
        repository.deleteAll().block();
    }

    private PricingRule buildRule(Long productId,
                                 Long zoneId,
                                 PricingRuleStatus status){
        return PricingRule.builder()
                .productId(productId)
                .baseAnnualRate(new BigDecimal("0.100000"))
                .minimumPremium(new BigDecimal("1000.00"))
                .termFactor(new BigDecimal("1.0000"))
                .locationZoneId(zoneId)
                .locationFactor(new BigDecimal("1.0000"))
                .status(status)
                .effectiveFrom(LocalDate.of(2025,1,1))
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveAndFindById(){
        PricingRule rule=buildRule(1L,null,PricingRuleStatus.DRAFT);
        PricingRule saved=repository.save(rule).block();

        StepVerifier.create(repository.findById(saved.getId()))
                .assertNext(found->{
                    assertThat(found.getProductId()).isEqualTo(1L);
                    assertThat(found.getStatus())
                            .isEqualTo(PricingRuleStatus.DRAFT);
                })
                .verifyComplete();
    }

    @Test
    void findAllByOrderByCreatedAtDesc(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.DRAFT))
                .block();
        repository.save(
                buildRule(2L,null,PricingRuleStatus.ACTIVE))
                .block();

        StepVerifier.create(repository.findAllByOrderByCreatedAtDesc())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByProductIdOrderByCreatedAtDesc(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.DRAFT))
                .block();
        repository.save(
                buildRule(1L,2L,PricingRuleStatus.ACTIVE))
                .block();
        repository.save(
                buildRule(2L,null,PricingRuleStatus.ACTIVE))
                .block();

        StepVerifier.create(
                repository.findAllByProductIdOrderByCreatedAtDesc(1L)
        )
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void findAllByStatusOrderByCreatedAtDesc(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.DRAFT))
                .block();
        repository.save(
                buildRule(2L,null,PricingRuleStatus.ACTIVE))
                .block();

        StepVerifier.create(
                repository.findAllByStatusOrderByCreatedAtDesc(
                        PricingRuleStatus.DRAFT)
        )
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void existsByProductIdAndLocationZoneIdAndStatus(){
        repository.save(
                buildRule(1L,2L,PricingRuleStatus.ACTIVE))
                .block();

        StepVerifier.create(repository
                .existsByProductIdAndLocationZoneIdAndStatus(
                        1L,2L,PricingRuleStatus.ACTIVE))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(repository
                .existsByProductIdAndLocationZoneIdAndStatus(
                        1L,2L,PricingRuleStatus.DRAFT))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void existsByProductIdAndLocationZoneIdIsNullAndStatus(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.ACTIVE))
                .block();

        StepVerifier.create(repository
                .existsByProductIdAndLocationZoneIdIsNullAndStatus(
                        1L,PricingRuleStatus.ACTIVE))
                .assertNext(exists->assertThat(exists).isTrue())
                .verifyComplete();

        StepVerifier.create(repository
                .existsByProductIdAndLocationZoneIdIsNullAndStatus(
                        1L,PricingRuleStatus.DRAFT))
                .assertNext(exists->assertThat(exists).isFalse())
                .verifyComplete();
    }

    @Test
    void findAllByProductIdAndStatus(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.ACTIVE))
                .block();
        repository.save(
                buildRule(1L,2L,PricingRuleStatus.ACTIVE))
                .block();
        repository.save(
                buildRule(1L,null,PricingRuleStatus.DRAFT))
                .block();

        StepVerifier.create(
                repository.findAllByProductIdAndStatus(
                        1L,PricingRuleStatus.ACTIVE)
        )
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void countByStatus(){
        repository.save(
                buildRule(1L,null,PricingRuleStatus.ACTIVE))
                .block();
        repository.save(
                buildRule(2L,null,PricingRuleStatus.ACTIVE))
                .block();
        repository.save(
                buildRule(3L,null,PricingRuleStatus.DRAFT))
                .block();

        StepVerifier.create(
                repository.countByStatus(PricingRuleStatus.ACTIVE)
        )
                .assertNext(count->assertThat(count).isEqualTo(2L))
                .verifyComplete();
    }
}
