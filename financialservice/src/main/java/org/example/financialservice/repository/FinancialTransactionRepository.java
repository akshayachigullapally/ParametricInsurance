package org.example.financialservice.repository;

import java.math.BigDecimal;
import org.example.financialservice.model.FinancialTransaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FinancialTransactionRepository extends ReactiveCrudRepository<FinancialTransaction,Long>{
    Flux<FinancialTransaction> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId);
    Flux<FinancialTransaction> findAllByPolicyIdOrderByCreatedAtDesc(Long policyId);
    Flux<FinancialTransaction> findAllByQuoteIdOrderByCreatedAtDesc(Long quoteId);
    Flux<FinancialTransaction> findAllByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    Flux<FinancialTransaction> findAllByPayoutIdOrderByCreatedAtDesc(Long payoutId);
    Flux<FinancialTransaction> findAllByTransactionTypeOrderByCreatedAtDesc(String transactionType);
    Flux<FinancialTransaction> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(amount),0) FROM financial_transactions WHERE transaction_type=:type AND status='SUCCESS'")
    Mono<BigDecimal> sumSuccessfulByType(String type);
}
