package org.example.financialservice.repository;

import org.example.financialservice.model.PricingRule;
import org.example.financialservice.model.PricingRuleStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PricingRuleRepository extends ReactiveCrudRepository<PricingRule,Long>{
    Flux<PricingRule> findAllByOrderByCreatedAtDesc();
    Flux<PricingRule> findAllByProductIdOrderByCreatedAtDesc(Long productId);
    Flux<PricingRule> findAllByStatusOrderByCreatedAtDesc(PricingRuleStatus status);
    Flux<PricingRule> findAllByProductIdAndStatus(
            Long productId,
            PricingRuleStatus status
    );
    Mono<Long> countByStatus(PricingRuleStatus status);
}