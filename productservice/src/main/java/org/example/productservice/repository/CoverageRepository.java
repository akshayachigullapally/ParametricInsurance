package org.example.productservice.repository;

import org.example.productservice.model.Coverage;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface CoverageRepository extends ReactiveCrudRepository<Coverage,Long>{
    Flux<Coverage> findAllByProductId(Long productId);
    Flux<Coverage> findAllByProductIdAndActiveTrue(Long productId);
    Mono<Coverage> findByIdAndProductId(Long id,Long productId);
    Mono<Boolean> existsByProductIdAndCoverageCodeIgnoreCase(
            Long productId,
            String coverageCode
    );
    Mono<Void> deleteAllByProductId(Long productId);
}