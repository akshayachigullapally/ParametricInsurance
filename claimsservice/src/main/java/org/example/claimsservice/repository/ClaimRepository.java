package org.example.claimsservice.repository;

import org.example.claimsservice.model.Claim;
import org.example.claimsservice.model.ClaimStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;
import java.time.LocalDate;

public interface ClaimRepository extends ReactiveCrudRepository<Claim,Long>{
    Mono<Claim> findByClaimNumber(String claimNumber);
    Mono<Boolean> existsByClaimNumber(String claimNumber);
    Flux<Claim> findAllByCustomerId(Long customerId);
    Flux<Claim> findAllByCustomerIdAndStatus(Long customerId,String status);
    Flux<Claim> findAllByStatus(String status);
    Mono<Claim> findByIdAndCustomerId(Long id,Long customerId);
    Mono<Boolean> existsByCustomerIdAndPolicyIdAndStatus(
            Long customerId,
            Long policyId,
            String status
    );
    Mono<Boolean> existsByCustomerIdAndPolicyIdAndTriggerEventIdAndStatusNot(
            Long customerId,
            Long policyId,
            Long triggerEventId,
            ClaimStatus status
    );
        Mono<Boolean> existsByPolicyIdAndTriggerEventId(Long policyId,Long triggerEventId);

    Mono<Boolean> existsByCustomerIdAndPolicyIdAndIncidentDateAndStatusNot(
            Long customerId,
            Long policyId,
            LocalDate incidentDate,
            ClaimStatus status
    );

    Flux<Claim> findAllByManualReviewRequiredTrue();

    Mono<Claim> findByIdAndManualReviewRequiredTrue(Long id);
}