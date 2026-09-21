package org.example.authservice.repository;

import org.example.authservice.model.PasswordResetToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface PasswordResetTokenRepository extends ReactiveCrudRepository<PasswordResetToken,Long>{
    Mono<PasswordResetToken> findByToken(String token);
}
