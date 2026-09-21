package org.example.financialservice.repository;

import org.example.financialservice.model.ReconciliationRecord;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReconciliationRecordRepository extends ReactiveCrudRepository<ReconciliationRecord,Long>{
    Flux<ReconciliationRecord> findAllByOrderByCreatedAtDesc();
    Flux<ReconciliationRecord> findAllByTransactionTypeOrderByCreatedAtDesc(String transactionType);
    Flux<ReconciliationRecord> findAllByStatusOrderByCreatedAtDesc(String status);
    Mono<Boolean> existsByEntityIdAndTransactionType(Long entityId, String transactionType);
}
