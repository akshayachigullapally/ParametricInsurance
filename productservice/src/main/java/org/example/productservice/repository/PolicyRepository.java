package org.example.productservice.repository;

import org.example.productservice.model.Policy;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface PolicyRepository extends ReactiveCrudRepository<Policy,Long>{
    Mono<Policy> findByPolicyNumber(String policyNumber);
    Mono<Boolean> existsByPolicyNumber(String policyNumber);
    Flux<Policy> findAllByCustomerId(Long customerId);
    Flux<Policy> findAllByCustomerIdAndStatus(Long customerId,String status);
    Flux<Policy> findAllByStatus(String status);
    Flux<Policy> findAllByProductIdAndStatus(Long productId, String status);
    Mono<Policy> findByIdAndCustomerId(Long id,Long customerId);
    Mono<Boolean> existsByCustomerIdAndProductIdAndStatus(
            Long customerId,
            Long productId,
            String status
    );
}