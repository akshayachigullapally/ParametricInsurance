package org.example.claimsservice.repository;

import org.example.claimsservice.model.ClaimDocument;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClaimDocumentRepository extends ReactiveCrudRepository<ClaimDocument,Long>{
    Flux<ClaimDocument> findAllByClaimIdOrderByUploadedAtAsc(Long claimId);
    Mono<Long> countByClaimId(Long claimId);
    Mono<ClaimDocument> findByIdAndClaimId(Long id,Long claimId);
}
