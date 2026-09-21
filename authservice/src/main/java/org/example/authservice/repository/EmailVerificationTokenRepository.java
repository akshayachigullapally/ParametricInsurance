package org.example.authservice.repository;

import org.example.authservice.model.EmailVerificationToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface EmailVerificationTokenRepository extends ReactiveCrudRepository<EmailVerificationToken,Long>{
    Mono<EmailVerificationToken> findByToken(String token);
    Mono<EmailVerificationToken> findByUserIdAndVerifiedFalse(Long userId);
}
