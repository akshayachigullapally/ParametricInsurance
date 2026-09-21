package org.example.authservice.repository;

import org.example.authservice.model.Session;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface SessionRepository extends ReactiveCrudRepository<Session,Long>{
    Flux<Session> findAllByUserIdAndActiveTrue(Long userId);
    Mono<Session> findByIdAndUserId(Long id,Long userId);
    Mono<Session> findByRefreshTokenId(Long refreshTokenId);
}