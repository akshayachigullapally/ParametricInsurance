package org.example.triggerservice.repository;

import org.example.triggerservice.model.MonitorCheckpoint;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface MonitorCheckpointRepository extends ReactiveCrudRepository<MonitorCheckpoint,Long>{
    Mono<MonitorCheckpoint> findBySourceCodeAndLocationZoneId(
            String sourceCode,
            Long locationZoneId
    );

    @Query("""
            SELECT * FROM monitor_checkpoints
            WHERE source_code=:sourceCode
            AND location_zone_id=:locationZoneId
            """)
    Mono<MonitorCheckpoint> findCheckpoint(
            String sourceCode,
            Long locationZoneId
    );
}
