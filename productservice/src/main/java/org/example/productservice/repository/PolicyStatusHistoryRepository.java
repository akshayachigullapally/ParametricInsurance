package org.example.productservice.repository;

import org.example.productservice.model.PolicyStatusHistory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PolicyStatusHistoryRepository extends ReactiveCrudRepository<PolicyStatusHistory,Long>{
    Flux<PolicyStatusHistory> findAllByPolicyIdOrderByChangedAtDesc(Long policyId);
}
