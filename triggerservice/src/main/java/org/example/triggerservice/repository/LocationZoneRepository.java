package org.example.triggerservice.repository;

import org.example.triggerservice.model.LocationZone;
import org.example.triggerservice.model.LocationZoneStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LocationZoneRepository extends ReactiveCrudRepository<LocationZone,Long>{
    Mono<Boolean> existsByZoneCodeIgnoreCase(String zoneCode);
    Mono<LocationZone> findByZoneCodeIgnoreCase(String zoneCode);
    Flux<LocationZone> findAllByStatusNotOrderByZoneNameAsc(LocationZoneStatus status);
    Flux<LocationZone> findAllByStatusOrderByZoneNameAsc(LocationZoneStatus status);
    Flux<LocationZone> findAllByStateIgnoreCaseAndStatusOrderByZoneNameAsc(String state,LocationZoneStatus status);

    @Query("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM location_zones
            WHERE UPPER(zone_code)=UPPER(:zoneCode)
            AND id<>:zoneId
            """)
    Mono<Boolean> existsByZoneCodeAndIdNot(String zoneCode,Long zoneId);
}