package org.example.productservice.repository;

import org.example.productservice.model.PolicyTriggerSnapshot;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PolicyTriggerSnapshotRepository extends ReactiveCrudRepository<PolicyTriggerSnapshot,Long>{
    Mono<PolicyTriggerSnapshot> findByPolicyId(Long policyId);
}
