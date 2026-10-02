package org.example.triggerservice.repository;

import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ObservationRepository extends ReactiveCrudRepository<Observation,Long>{
    Mono<Boolean> existsByObservationReference(String observationReference);

    Mono<Boolean> existsBySourceReference(String sourceReference);

    Flux<Observation> findAllByOrderByObservedAtDesc();

    Mono<Observation> findFirstByLocationZoneIdAndMeasurementTypeOrderByObservedAtDesc(
            Long locationZoneId,
            MeasurementType measurementType
    );

    Flux<Observation> findAllByLocationZoneIdOrderByObservedAtDesc(
            Long locationZoneId
    );

    Flux<Observation> findAllByMeasurementTypeOrderByObservedAtDesc(
            MeasurementType measurementType
    );

    Flux<Observation> findAllByMeasurementTypeAndStatusOrderByObservedAtDesc(
            MeasurementType measurementType,
            ObservationStatus status
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

    Flux<Observation> findAllByStatusOrderByObservedAtDesc(
            ObservationStatus status
    );

    Flux<Observation> findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
            Long locationZoneId,
            MeasurementType measurementType,
            ObservationStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

}