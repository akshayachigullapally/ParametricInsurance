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
                .then(insertUser(2L,"role2@test.com"))).verifyComplete();
    }

    @Test
    void findAllByUserId_whenRolesExist_shouldReturnOnlyMatchingRoles() {
        StepVerifier.create(roleRepository.findByName("ROLE_CUSTOMER").zipWith(roleRepository.findByName("ROLE_UNDERWRITER"))
                        .flatMap(roles ->
                        userRoleRepository.saveAll(Flux.just(
                                UserRole.builder().userId(1L).roleId(roles.getT1().getId()).build(),
                                UserRole.builder().userId(1L).roleId(roles.getT2().getId()).build(),
                                UserRole.builder().userId(2L).roleId(roles.getT1().getId()).build()
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
