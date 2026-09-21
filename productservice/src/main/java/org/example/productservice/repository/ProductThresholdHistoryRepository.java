package org.example.productservice.repository;

import org.example.productservice.model.ProductThresholdHistory;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductThresholdHistoryRepository extends ReactiveCrudRepository<ProductThresholdHistory,Long>{
    Flux<ProductThresholdHistory> findAllByProductIdOrderByChangedAtDesc(Long productId);
    Mono<Boolean> existsByProductId(Long productId);
}