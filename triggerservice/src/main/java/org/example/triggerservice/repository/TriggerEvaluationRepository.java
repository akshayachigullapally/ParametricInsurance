package org.example.triggerservice.repository;

import org.example.triggerservice.model.TriggerEvaluation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TriggerEvaluationRepository
        extends ReactiveCrudRepository<TriggerEvaluation,Long>{

    Flux<TriggerEvaluation> findAllByOrderByEvaluatedAtDesc();

    Flux<TriggerEvaluation>
    findAllByTriggerDefinitionIdOrderByEvaluatedAtDesc(
            Long triggerDefinitionId
    );
}