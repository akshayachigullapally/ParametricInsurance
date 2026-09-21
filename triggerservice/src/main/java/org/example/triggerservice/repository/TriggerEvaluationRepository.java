package org.example.triggerservice.repository;

import org.example.triggerservice.model.TriggerEvaluation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TriggerEvaluationRepository
        extends ReactiveCrudRepository<TriggerEvaluation,Long>{

    Mono<Boolean> existsByEvaluationReference(String evaluationReference);

    Flux<TriggerEvaluation> findAllByOrderByEvaluatedAtDesc();

    Flux<TriggerEvaluation>
    findAllByTriggerDefinitionIdOrderByEvaluatedAtDesc(
            Long triggerDefinitionId
    );
}