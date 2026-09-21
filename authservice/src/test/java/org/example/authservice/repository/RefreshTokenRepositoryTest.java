package org.example.authservice.repository;

import org.example.authservice.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryTest extends BaseRepositoryTest {
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"token1@test.com"))
                .then(insertUser(2L,"token2@test.com"))).expectNextCount(1).verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> insertUser(Long id,String email) {
        return databaseClient.sql("INSERT INTO users_credentials(id,email,password,full_name,role,enabled,email_verified,created_at,updated_at) VALUES(:id,:email,'password','Test User','ROLE_CUSTOMER',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").bind("id",id).bind("email",email).fetch().rowsUpdated();
    }

    @Test
    void findByToken_whenTokenExists_shouldReturnRefreshToken() {
        StepVerifier.create(refreshTokenRepository.save(token(1L,"token-1",false)).then(refreshTokenRepository.findByToken("token-1")))
                .assertNext(found->{assertThat(found.getToken()).isEqualTo("token-1");assertThat(found.getRevoked()).isFalse();}).verifyComplete();
    }

    @Test
    void findByToken_whenTokenDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(refreshTokenRepository.findByToken("missing")).verifyComplete();
    }

    @Test
    void findAllByUserIdAndRevokedFalse_whenTokensExist_shouldReturnOnlyNonRevokedTokens() {
        StepVerifier.create(refreshTokenRepository.saveAll(Flux.just(token(1L,"active-1",false),token(1L,"revoked-1",true),token(2L,"active-2",false)))
                .thenMany(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L)).collectList())
                .assertNext(result->{assertThat(result).hasSize(1);assertThat(result.get(0).getToken()).isEqualTo("active-1");}).verifyComplete();
    }

    @Test
    void findAllByUserIdAndRevokedFalse_whenOnlyRevokedTokensExist_shouldReturnEmptyFlux() {
        StepVerifier.create(refreshTokenRepository.save(token(1L,"revoked-only",true)).thenMany(refreshTokenRepository.findAllByUserIdAndRevokedFalse(1L))).verifyComplete();
    }

    private RefreshToken token(Long userId,String value,boolean revoked) {
        LocalDateTime now=LocalDateTime.now();
        return RefreshToken.builder().userId(userId).token(value).expiresAt(now.plusDays(7)).revoked(revoked).createdAt(now).build();
    }
}