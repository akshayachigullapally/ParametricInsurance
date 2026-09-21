package org.example.authservice.repository;

import org.example.authservice.model.UserCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserCredentialRepositoryTest extends BaseRepositoryTest {
    @Autowired private UserCredentialRepository userCredentialRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())).expectNextCount(1).verifyComplete();
    }

    @Test
    void findByEmailIgnoreCase_whenEmailCaseDiffers_shouldReturnCredential() {
        StepVerifier.create(userCredentialRepository.save(user("Akshaya@Test.com")).then(userCredentialRepository.findByEmailIgnoreCase("akshaya@test.com")))
                .assertNext(found->{assertThat(found.getId()).isNotNull();assertThat(found.getEmail()).isEqualTo("Akshaya@Test.com");}).verifyComplete();
    }

    @Test
    void findByEmailIgnoreCase_whenEmailDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(userCredentialRepository.findByEmailIgnoreCase("missing@test.com")).verifyComplete();
    }

    @Test
    void existsByEmailIgnoreCase_whenEmailExists_shouldReturnTrue() {
        StepVerifier.create(userCredentialRepository.save(user("user@test.com")).then(userCredentialRepository.existsByEmailIgnoreCase("USER@TEST.COM"))).expectNext(true).verifyComplete();
    }

    @Test
    void existsByEmailIgnoreCase_whenEmailDoesNotExist_shouldReturnFalse() {
        StepVerifier.create(userCredentialRepository.existsByEmailIgnoreCase("missing@test.com")).expectNext(false).verifyComplete();
    }

    private UserCredential user(String email) {
        LocalDateTime now=LocalDateTime.now();
        return UserCredential.builder().email(email).password("encoded-password").fullName("Test User").role("ROLE_CUSTOMER").enabled(true).emailVerified(false).createdAt(now).updatedAt(now).build();
    }
}