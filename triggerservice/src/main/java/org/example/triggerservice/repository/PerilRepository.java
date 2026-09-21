package org.example.triggerservice.repository;

import org.example.triggerservice.model.Peril;
import org.example.triggerservice.model.PerilStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PerilRepository extends ReactiveCrudRepository<Peril,Long>{
    Mono<Boolean> existsByPerilCodeIgnoreCase(String perilCode);
    Mono<Peril> findByPerilCodeIgnoreCase(String perilCode);
    Flux<Peril> findAllByStatusNotOrderByPerilNameAsc(PerilStatus status);
    Flux<Peril> findAllByStatusOrderByPerilNameAsc(PerilStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM perils
            WHERE UPPER(peril_code)=UPPER(:perilCode)
            AND id<>:perilId
            """)
    Mono<Boolean> existsByPerilCodeAndIdNot(String perilCode,Long perilId);
}
