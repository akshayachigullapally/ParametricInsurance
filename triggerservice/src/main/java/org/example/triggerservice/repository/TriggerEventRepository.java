package org.example.triggerservice.repository;

import org.example.triggerservice.model.TriggerEvent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface TriggerEventRepository extends ReactiveCrudRepository<TriggerEvent,Long>{

    Flux<TriggerEvent> findAllByOrderByDetectedAtDesc();

}