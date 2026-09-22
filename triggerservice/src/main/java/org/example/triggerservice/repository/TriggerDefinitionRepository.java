package org.example.triggerservice.repository;

import org.example.triggerservice.model.MeasurementType;
import org.example.triggerservice.model.TriggerDefinition;
import org.example.triggerservice.model.TriggerDefinitionStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TriggerDefinitionRepository extends ReactiveCrudRepository<TriggerDefinition,Long>{
    Mono<Boolean> existsByDefinitionCodeIgnoreCase(String definitionCode);

    Flux<TriggerDefinition> findAllByStatusNotOrderByDefinitionNameAsc(
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByStatusOrderByDefinitionNameAsc(
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByPerilIdAndStatusNotOrderByDefinitionNameAsc(
            Long perilId,
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByLocationZoneIdAndStatusNotOrderByDefinitionNameAsc(
            Long locationZoneId,
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByProductIdAndStatusNotOrderByDefinitionNameAsc(
            Long productId,
            TriggerDefinitionStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM trigger_definitions
            WHERE UPPER(definition_code)=UPPER(:definitionCode)
            AND id<>:definitionId
            """)
    Mono<Boolean> existsByDefinitionCodeAndIdNot(
            String definitionCode,
            Long definitionId
    );

    @Query("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM trigger_definitions
            WHERE peril_id=:perilId
            AND location_zone_id=:locationZoneId
            AND product_id=:productId
            AND measurement_type=:measurementType
            AND status<>'ARCHIVED'
            """)
    Mono<Boolean> existsSimilarDefinition(
            Long perilId,
            Long locationZoneId,
            Long productId,
            String measurementType
    );

    @Query("""
            SELECT CASE WHEN COUNT(*) > 0 THEN TRUE ELSE FALSE END
            FROM trigger_definitions
            WHERE peril_id=:perilId
            AND location_zone_id=:locationZoneId
            AND product_id=:productId
            AND measurement_type=:measurementType
            AND status<>'ARCHIVED'
            AND id<>:definitionId
            """)
    Mono<Boolean> existsSimilarDefinitionAndIdNot(
            Long perilId,
            Long locationZoneId,
            Long productId,
            String measurementType,
            Long definitionId
    );
    Mono<TriggerDefinition> findByDefinitionCodeIgnoreCase(String definitionCode);

    Mono<TriggerDefinition> findByProductIdAndMeasurementTypeAndLocationZoneIdAndStatusNot(
            Long productId,
            MeasurementType measurementType,
            Long locationZoneId,
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByProductIdAndStatusNot(
            Long productId,
            TriggerDefinitionStatus status
    );

    Flux<TriggerDefinition> findAllByLocationZoneIdAndMeasurementTypeAndStatus(
            Long locationZoneId,
            MeasurementType measurementType,
            TriggerDefinitionStatus status
    );
}