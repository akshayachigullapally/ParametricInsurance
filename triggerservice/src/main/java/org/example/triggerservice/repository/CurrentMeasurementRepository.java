package org.example.triggerservice.repository;

import org.example.triggerservice.model.CurrentMeasurement;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CurrentMeasurementRepository extends ReactiveCrudRepository<CurrentMeasurement,Long>{
    Mono<CurrentMeasurement> findByProductIdAndLocationZoneId(Long productId,Long locationZoneId);
}
