package org.example.authservice.repository;

import org.example.authservice.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleRepositoryTest extends BaseRepositoryTest {
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"role1@test.com"))
                .then(insertUser(2L,"role2@test.com"))).expectNextCount(1).verifyComplete();
    }

    private reactor.core.publisher.Mono<Long> insertUser(Long id,String email) {
        return databaseClient.sql("INSERT INTO users_credentials(id,email,password,full_name,role,enabled,email_verified,created_at,updated_at) VALUES(:id,:email,'password','Test User','ROLE_CUSTOMER',TRUE,FALSE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").bind("id",id).bind("email",email).fetch().rowsUpdated();
    }

    @Test
    void findAllByUserId_whenRolesExist_shouldReturnOnlyMatchingRoles() {
        StepVerifier.create(roleRepository.findByName("ROLE_CUSTOMER").flatMap(role ->
                        userRoleRepository.saveAll(Flux.just(
                                UserRole.builder().userId(1L).roleId(role.getId()).build(),
                                UserRole.builder().userId(1L).roleId(role.getId()).build(),
                                UserRole.builder().userId(2L).roleId(role.getId()).build()
                        )).thenMany(userRoleRepository.findAllByUserId(1L)).collectList()))
                .assertNext(result -> {
                    assertThat(result).hasSize(2);
                    assertThat(result).extracting(UserRole::getUserId).containsOnly(1L);
                }).verifyComplete();
    }

    @Test
    void findAllByUserId_whenNoRolesExist_shouldReturnEmptyFlux() {
        StepVerifier.create(userRoleRepository.findAllByUserId(99L)).verifyComplete();
    }
}
