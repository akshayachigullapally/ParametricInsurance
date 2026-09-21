package org.example.financialservice.repository;

import java.math.BigDecimal;
import java.util.List;
import org.example.financialservice.model.Payment;
import org.example.financialservice.model.PaymentStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentRepository extends ReactiveCrudRepository<Payment,Long>{
    Flux<Payment> findAllByOrderByCreatedAtDesc();
    Flux<Payment> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Mono<Payment> findByIdAndCustomerId(Long id, Long customerId);
    Mono<Boolean> existsByPolicyIdAndStatus(
            Long policyId,
            PaymentStatus status
    );
    Mono<Long> countByPolicyId(Long policyId);
    Mono<Long> countByStatus(PaymentStatus status);
    Flux<Payment> findAllByPolicyIdOrderByCreatedAtDesc(Long policyId);
    Flux<Payment> findAllByStatus(PaymentStatus status);
    Flux<Payment> findAllByStatusIn(List<PaymentStatus> statuses);
    Flux<Payment> findAllByStatusAndAttemptNumberLessThan(
            PaymentStatus status,
            int maxAttempts
    );

    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='SUCCESS'")
    Mono<BigDecimal> sumSuccessfulAmount();
}