package org.example.authservice.repository;

import org.example.authservice.model.UserRole;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface UserRoleRepository extends ReactiveCrudRepository<UserRole,Long>{
}
