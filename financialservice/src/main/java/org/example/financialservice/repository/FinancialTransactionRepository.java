package org.example.financialservice.repository;

import org.example.financialservice.model.FinancialTransaction;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface FinancialTransactionRepository extends ReactiveCrudRepository<FinancialTransaction,Long>{
    Flux<FinancialTransaction> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Flux<FinancialTransaction> findAllByPolicyIdOrderByCreatedAtDesc(Long policyId);
    Flux<FinancialTransaction> findAllByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    Flux<FinancialTransaction> findAllByPayoutIdOrderByCreatedAtDesc(Long payoutId);
    Flux<FinancialTransaction> findAllByTransactionTypeOrderByCreatedAtDesc(String transactionType);
    Flux<FinancialTransaction> findAllByOrderByCreatedAtDesc();
}
