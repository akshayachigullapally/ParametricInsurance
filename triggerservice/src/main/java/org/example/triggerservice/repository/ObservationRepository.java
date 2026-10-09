package org.example.triggerservice.repository;

import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.Observation;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ObservationRepository extends ReactiveCrudRepository<Observation,Long>{

    Mono<Observation> findFirstByLocationZoneIdAndMeasurementTypeOrderByObservedAtDesc(
            Long locationZoneId,
            MeasurementType measurementType
    );

    @Query("SELECT * FROM observations "
            +"WHERE measurement_type=:measurementType AND status='VALIDATED' "
            +"AND observed_at=(SELECT MAX(observed_at) FROM observations "
            +"WHERE measurement_type=:measurementType AND status='VALIDATED') "
            +"LIMIT 1")
    Mono<Observation> findLatestValidated(String measurementType);

    @Modifying
    @Query("DELETE FROM observations WHERE source='MOCK' AND observed_at<:cutoff")
    Mono<Integer> deleteMockObservedBefore(LocalDateTime cutoff);

}