package org.example.authservice.repository;

import org.example.authservice.model.UserCredential;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.*;

public interface UserCredentialRepository extends ReactiveCrudRepository<UserCredential,Long>{
    Mono<UserCredential> findByEmailIgnoreCase(String email);
    Mono<Boolean> existsByEmailIgnoreCase(String email);
}