package org.example.claimsservice.repository;

import org.example.claimsservice.model.ClaimTimeline;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ClaimTimelineRepository extends ReactiveCrudRepository<ClaimTimeline,Long>{
    Flux<ClaimTimeline> findAllByClaimIdOrderByCreatedAtAsc(Long claimId);
    Flux<ClaimTimeline> findAllByActionByAndActionByRoleOrderByCreatedAtDesc(
            Long actionBy,String actionByRole);
}
