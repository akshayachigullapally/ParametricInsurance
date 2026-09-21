package org.example.authservice.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RoleRepositoryTest extends BaseRepositoryTest {
    @Autowired private RoleRepository roleRepository;

    @Test
    void findByName_whenSeededRoleExists_shouldReturnRole() {
        StepVerifier.create(roleRepository.findByName("ROLE_CUSTOMER"))
                .assertNext(role->{assertThat(role.getId()).isNotNull();assertThat(role.getName()).isEqualTo("ROLE_CUSTOMER");}).verifyComplete();
    }

    @Test
    void findByName_whenRoleDoesNotExist_shouldReturnEmptyMono() {
        StepVerifier.create(roleRepository.findByName("ROLE_UNKNOWN")).verifyComplete();
    }
}