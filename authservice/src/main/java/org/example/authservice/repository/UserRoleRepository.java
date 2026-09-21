package org.example.authservice.repository;

import org.example.authservice.model.UserRole;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRole,Long>{
    Flux<UserRole> findAllByUserId(Long userId);
}
