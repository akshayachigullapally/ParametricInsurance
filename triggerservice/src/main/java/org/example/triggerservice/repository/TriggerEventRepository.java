package org.example.triggerservice.repository;

import org.example.triggerservice.model.TriggerEvent;
import org.example.triggerservice.model.TriggerEventStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

public interface TriggerEventRepository extends ReactiveCrudRepository<TriggerEvent,Long>{
    Mono<Boolean> existsByEventReference(String eventReference);

    Mono<TriggerEvent> findByTriggerEvaluationId(Long triggerEvaluationId);

    Mono<Boolean> existsByTriggerDefinitionIdAndWindowStartAndWindowEndAndStatusNot(
            Long triggerDefinitionId,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            TriggerEventStatus status
    );

    Flux<TriggerEvent> findAllByOrderByDetectedAtDesc();

    Flux<TriggerEvent> findAllByStatusOrderByDetectedAtDesc(
            TriggerEventStatus status
    );

    Flux<TriggerEvent> findAllByTriggerDefinitionIdOrderByDetectedAtDesc(
            Long triggerDefinitionId
    );

    Flux<TriggerEvent> findAllByProductIdOrderByDetectedAtDesc(
            Long productId
    );

    Flux<TriggerEvent> findAllByLocationZoneIdOrderByDetectedAtDesc(
            Long locationZoneId
    );

    Flux<TriggerEvent> findAllByProductIdAndStatusOrderByDetectedAtDesc(
            Long productId,
            TriggerEventStatus status
    );
}