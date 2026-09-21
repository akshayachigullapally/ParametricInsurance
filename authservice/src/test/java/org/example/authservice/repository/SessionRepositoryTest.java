package org.example.authservice.repository;

import org.example.authservice.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRepositoryTest extends BaseRepositoryTest {
    @Autowired private SessionRepository sessionRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"session1@test.com"))
                .then(insertUser(2L,"session2@test.com"))
                .then(insertToken(101L,1L,"session-token-101"))
                .then(insertToken(102L,1L,"session-token-102"))
                .then(insertToken(103L,2L,"session-token-103"))).expectNextCount(1).verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> insertUser(Long id,String email) {
        return databaseClient.sql("INSERT INTO users_credentials(id,email,password,full_name,role,enabled,email_verified,created_at,updated_at) VALUES(:id,:email,'password','Test User','ROLE_CUSTOMER',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").bind("id",id).bind("email",email).fetch().rowsUpdated();
    }

    private reactor.core.publisher.Mono<Long> insertToken(Long id,Long userId,String token) {
        return databaseClient.sql("INSERT INTO refresh_tokens(id,user_id,token,expires_at,revoked,created_at) VALUES(:id,:userId,:token,DATEADD('DAY',7,CURRENT_TIMESTAMP),FALSE,CURRENT_TIMESTAMP)").bind("id",id).bind("userId",userId).bind("token",token).fetch().rowsUpdated();
    }

    @Test
    void findAllByUserIdAndActiveTrue_whenSessionsExist_shouldReturnOnlyActiveSessions() {
        StepVerifier.create(sessionRepository.saveAll(Flux.just(session(1L,101L,"Chrome",true),session(1L,102L,"Mobile",false),session(2L,103L,"Edge",true)))
                .thenMany(sessionRepository.findAllByUserIdAndActiveTrue(1L)).collectList())
                .assertNext(result->{assertThat(result).hasSize(1);assertThat(result.get(0).getDeviceName()).isEqualTo("Chrome");}).verifyComplete();
    }

    @Test
    void findAllByUserIdAndActiveTrue_whenNoActiveSessionExists_shouldReturnEmptyFlux() {
        StepVerifier.create(sessionRepository.save(session(1L,101L,"Chrome",false)).thenMany(sessionRepository.findAllByUserIdAndActiveTrue(1L))).verifyComplete();
    }

    @Test
    void findByIdAndUserId_whenBothMatch_shouldReturnSession() {
        StepVerifier.create(sessionRepository.save(session(1L,101L,"Chrome",true)).flatMap(saved->sessionRepository.findByIdAndUserId(saved.getId(),1L)))
                .assertNext(found->assertThat(found.getDeviceName()).isEqualTo("Chrome")).verifyComplete();
    }

    @Test
    void findByIdAndUserId_whenUserDoesNotMatch_shouldReturnEmptyMono() {
        StepVerifier.create(sessionRepository.save(session(1L,101L,"Chrome",true)).flatMap(saved->sessionRepository.findByIdAndUserId(saved.getId(),2L))).verifyComplete();
    }

    @Test
    void findByRefreshTokenId_whenSessionExists_shouldReturnSession() {
        StepVerifier.create(sessionRepository.save(session(1L,101L,"Chrome",true)).then(sessionRepository.findByRefreshTokenId(101L)))
                .assertNext(found->assertThat(found.getRefreshTokenId()).isEqualTo(101L)).verifyComplete();
    }

    @Test
    void findByRefreshTokenId_whenSessionDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(sessionRepository.findByRefreshTokenId(999L)).verifyComplete();
    }

    private Session session(Long userId,Long tokenId,String device,boolean active) {
        LocalDateTime now=LocalDateTime.now();
        return Session.builder().userId(userId).refreshTokenId(tokenId).deviceName(device).ipAddress("127.0.0.1").active(active).createdAt(now).lastAccessedAt(now).build();
    }
}