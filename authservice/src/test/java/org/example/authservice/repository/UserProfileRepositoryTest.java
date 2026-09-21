package org.example.authservice.repository;

import org.example.authservice.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileRepositoryTest extends BaseRepositoryTest {
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"profile1@test.com"))
                .then(insertUser(2L,"profile2@test.com"))
                .then(insertUser(3L,"profile3@test.com"))).expectNextCount(1).verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> insertUser(Long id,String email) {
        return databaseClient.sql("INSERT INTO users_credentials(id,email,password,full_name,role,enabled,email_verified,created_at,updated_at) VALUES(:id,:email,'password','Test User','ROLE_CUSTOMER',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").bind("id",id).bind("email",email).fetch().rowsUpdated();
    }

    @Test
    void findByAuthUserId_whenProfileExists_shouldReturnProfile() {
        StepVerifier.create(userProfileRepository.save(profile(1L,"ACTIVE","PREMIUM")).then(userProfileRepository.findByAuthUserId(1L)))
                .assertNext(found->{assertThat(found.getId()).isNotNull();assertThat(found.getCustomerSegment()).isEqualTo("PREMIUM");}).verifyComplete();
    }

    @Test
    void findByAuthUserId_whenProfileDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(userProfileRepository.findByAuthUserId(99L)).verifyComplete();
    }

    @Test
    void existsByAuthUserId_whenProfileExists_shouldReturnTrue() {
        StepVerifier.create(userProfileRepository.save(profile(1L,"ACTIVE","PREMIUM")).then(userProfileRepository.existsByAuthUserId(1L))).expectNext(true).verifyComplete();
    }

    @Test
    void existsByAuthUserId_whenProfileDoesNotExist_shouldReturnFalse() {
        StepVerifier.create(userProfileRepository.existsByAuthUserId(99L)).expectNext(false).verifyComplete();
    }

    @Test
    void findAllByStatus_whenProfilesExist_shouldReturnOnlyMatchingProfiles() {
        StepVerifier.create(userProfileRepository.saveAll(Flux.just(profile(1L,"ACTIVE","PREMIUM"),profile(2L,"INACTIVE","STANDARD"),profile(3L,"ACTIVE","STANDARD")))
                .thenMany(userProfileRepository.findAllByStatus("ACTIVE")).collectList())
                .assertNext(result->{assertThat(result).hasSize(2);assertThat(result).extracting(UserProfile::getStatus).containsOnly("ACTIVE");}).verifyComplete();
    }

    @Test
    void findAllByCustomerSegment_whenProfilesExist_shouldReturnOnlyMatchingProfiles() {
        StepVerifier.create(userProfileRepository.saveAll(Flux.just(profile(1L,"ACTIVE","PREMIUM"),profile(2L,"ACTIVE","STANDARD"),profile(3L,"INACTIVE","PREMIUM")))
                .thenMany(userProfileRepository.findAllByCustomerSegment("PREMIUM")).collectList())
                .assertNext(result->{assertThat(result).hasSize(2);assertThat(result).extracting(UserProfile::getCustomerSegment).containsOnly("PREMIUM");}).verifyComplete();
    }

    @Test
    void findAllByStatusAndCustomerSegment_whenBothMatch_shouldReturnMatchingProfiles() {
        StepVerifier.create(userProfileRepository.saveAll(Flux.just(profile(1L,"ACTIVE","PREMIUM"),profile(2L,"ACTIVE","STANDARD"),profile(3L,"INACTIVE","PREMIUM")))
                .thenMany(userProfileRepository.findAllByStatusAndCustomerSegment("ACTIVE","PREMIUM")).collectList())
                .assertNext(result->{assertThat(result).hasSize(1);assertThat(result.get(0).getAuthUserId()).isEqualTo(1L);}).verifyComplete();
    }

    @Test
    void findAllByStatusAndCustomerSegment_whenOnlyOneFieldMatches_shouldReturnEmptyFlux() {
        StepVerifier.create(userProfileRepository.save(profile(1L,"ACTIVE","STANDARD")).thenMany(userProfileRepository.findAllByStatusAndCustomerSegment("ACTIVE","PREMIUM"))).verifyComplete();
    }

    private UserProfile profile(Long authUserId,String status,String segment) {
        LocalDateTime now=LocalDateTime.now();
        return UserProfile.builder().authUserId(authUserId).phoneNumber("9000000000").dateOfBirth(LocalDate.of(2000,1,1)).customerSegment(segment).status(status).createdAt(now).updatedAt(now).build();
    }
}