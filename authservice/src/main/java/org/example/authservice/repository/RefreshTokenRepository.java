package org.example.authservice.repository;

import org.example.authservice.model.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken,Long>{
    Mono<RefreshToken> findByToken(String token);
    Flux<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);
}