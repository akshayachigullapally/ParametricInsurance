package org.example.authservice.repository;

import org.example.authservice.model.UserProfile;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserProfileRepository extends ReactiveCrudRepository<UserProfile,Long>{
    Mono<UserProfile> findByAuthUserId(Long authUserId);
    Flux<UserProfile> findAllByStatus(String status);
    Flux<UserProfile> findAllByCustomerSegment(String customerSegment);
    Flux<UserProfile> findAllByStatusAndCustomerSegment(
            String status,
            String customerSegment
    );
}