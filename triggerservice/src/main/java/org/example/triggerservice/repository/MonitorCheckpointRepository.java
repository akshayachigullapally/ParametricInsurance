package org.example.triggerservice.repository;

import org.example.triggerservice.model.MonitorCheckpoint;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface MonitorCheckpointRepository extends ReactiveCrudRepository<MonitorCheckpoint,Long>{
}
