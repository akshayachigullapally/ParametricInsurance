package org.example.triggerservice.repository;

import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.Observation;
import org.example.triggerservice.model.ObservationStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface ObservationRepository extends ReactiveCrudRepository<Observation,Long>{
    Mono<Boolean> existsByObservationReference(String observationReference);

    Mono<Boolean> existsBySourceReference(String sourceReference);

    Flux<Observation> findAllByOrderByObservedAtDesc();

    Flux<Observation> findAllByLocationZoneIdOrderByObservedAtDesc(
            Long locationZoneId
    );

    Flux<Observation> findAllByMeasurementTypeOrderByObservedAtDesc(
            MeasurementType measurementType
    );

    Flux<Observation> findAllByLocationZoneIdAndMeasurementTypeAndStatusAndObservedAtBetweenOrderByObservedAtAsc(
            Long locationZoneId,
            MeasurementType measurementType,
            ObservationStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

}