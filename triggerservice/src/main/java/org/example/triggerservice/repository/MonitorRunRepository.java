package org.example.triggerservice.repository;

import org.example.triggerservice.model.MonitorRun;
import org.example.triggerservice.model.MonitorRunStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MonitorRunRepository extends ReactiveCrudRepository<MonitorRun,Long>{
    Flux<MonitorRun> findAllByOrderByStartedAtDesc();

    Mono<MonitorRun> findTopByStatusOrderByStartedAtDesc(MonitorRunStatus status);
}
