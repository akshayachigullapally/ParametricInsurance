package org.example.authservice.repository;

import org.example.authservice.model.PasswordResetToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenRepositoryTest extends BaseRepositoryTest {
    @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM password_reset_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"pr1@test.com"))
                .then(insertUser(2L,"pr2@test.com"))).verifyComplete();
    }

    @Test
    void findByToken_whenTokenExists_shouldReturnPasswordResetToken() {
        StepVerifier.create(passwordResetTokenRepository.save(token(1L,"reset-token-1",false))
                        .then(passwordResetTokenRepository.findByToken("reset-token-1")))
                .assertNext(found -> {
                    assertThat(found.getToken()).isEqualTo("reset-token-1");
                    assertThat(found.getRevoked()).isFalse();
                    assertThat(found.getUserId()).isEqualTo(1L);
                }).verifyComplete();
    }

    @Test
    void findByToken_whenTokenDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(passwordResetTokenRepository.findByToken("missing-token")).verifyComplete();
    }

    private PasswordResetToken token(Long userId,String value,boolean revoked) {
        LocalDateTime now=LocalDateTime.now();
        return PasswordResetToken.builder().userId(userId).token(value).expiresAt(now.plusHours(1)).revoked(revoked).createdAt(now).build();
    }
}
