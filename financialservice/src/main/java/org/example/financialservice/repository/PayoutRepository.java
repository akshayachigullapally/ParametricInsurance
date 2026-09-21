package org.example.financialservice.repository;

import java.math.BigDecimal;
import org.example.financialservice.model.Payout;
import org.example.financialservice.model.PayoutStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PayoutRepository extends ReactiveCrudRepository<Payout,Long>{
    Flux<Payout> findAllByOrderByCreatedAtDesc();
    Flux<Payout> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Mono<Payout> findByIdAndCustomerId(Long id, Long customerId);
    Mono<Boolean> existsByClaimId(Long claimId);
    Flux<Payout> findAllByClaimIdOrderByCreatedAtDesc(Long claimId);
    Mono<Long> countByStatus(PayoutStatus status);
    Flux<Payout> findAllByStatus(PayoutStatus status);
    Flux<Payout> findAllByStatusAndRetryCountLessThan(
            PayoutStatus status,
            int maxRetries
    );

    @Query("SELECT COALESCE(SUM(amount),0) FROM payouts WHERE status='SUCCESS'")
    Mono<BigDecimal> sumSuccessfulAmount();
}