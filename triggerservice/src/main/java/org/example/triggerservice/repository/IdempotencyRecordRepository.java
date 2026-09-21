package org.example.triggerservice.repository;

import org.example.triggerservice.model.IdempotencyRecord;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface IdempotencyRecordRepository extends ReactiveCrudRepository<IdempotencyRecord,Long>{
    Mono<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    Mono<Boolean> existsByIdempotencyKey(String idempotencyKey);
}
