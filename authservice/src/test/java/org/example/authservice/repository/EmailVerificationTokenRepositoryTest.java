package org.example.authservice.repository;

import org.example.authservice.model.EmailVerificationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationTokenRepositoryTest extends BaseRepositoryTest {
    @Autowired private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM email_verification_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"ev1@test.com"))
                .then(insertUser(2L,"ev2@test.com"))).expectNextCount(1).verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> insertUser(Long id,String email) {
        return databaseClient.sql("INSERT INTO users_credentials(id,email,password,full_name,role,enabled,email_verified,created_at,updated_at) VALUES(:id,:email,'password','Test User','ROLE_CUSTOMER',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").bind("id",id).bind("email",email).fetch().rowsUpdated();
    }

    @Test
    void findByToken_whenTokenExists_shouldReturnEmailVerificationToken() {
        StepVerifier.create(emailVerificationTokenRepository.save(token(1L,"verify-token-1",false))
                        .then(emailVerificationTokenRepository.findByToken("verify-token-1")))
                .assertNext(found -> {
                    assertThat(found.getToken()).isEqualTo("verify-token-1");
                    assertThat(found.getVerified()).isFalse();
                    assertThat(found.getUserId()).isEqualTo(1L);
                }).verifyComplete();
    }

    @Test
    void findByToken_whenTokenDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(emailVerificationTokenRepository.findByToken("missing-token")).verifyComplete();
    }

    @Test
    void findByUserIdAndVerifiedFalse_whenUnverifiedTokenExists_shouldReturnToken() {
        StepVerifier.create(emailVerificationTokenRepository.save(token(1L,"verify-token-1",false))
                        .then(emailVerificationTokenRepository.findByUserIdAndVerifiedFalse(1L)))
                .assertNext(found -> {
                    assertThat(found.getUserId()).isEqualTo(1L);
                    assertThat(found.getVerified()).isFalse();
                    assertThat(found.getToken()).isEqualTo("verify-token-1");
                }).verifyComplete();
    }

    @Test
    void findByUserIdAndVerifiedFalse_whenAllTokensVerified_shouldReturnEmptyMono() {
        StepVerifier.create(emailVerificationTokenRepository.save(token(1L,"verify-token-1",true))
                        .then(emailVerificationTokenRepository.findByUserIdAndVerifiedFalse(1L)))
                .verifyComplete();
    }

    @Test
    void findByUserIdAndVerifiedFalse_whenNoTokensExist_shouldReturnEmptyMono() {
        StepVerifier.create(emailVerificationTokenRepository.findByUserIdAndVerifiedFalse(99L)).verifyComplete();
    }

    private EmailVerificationToken token(Long userId,String value,boolean verified) {
        LocalDateTime now=LocalDateTime.now();
        return EmailVerificationToken.builder().userId(userId).token(value).expiresAt(now.plusHours(24)).verified(verified).createdAt(now).build();
    }
}
