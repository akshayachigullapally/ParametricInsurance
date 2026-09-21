package org.example.authservice.repository;

import org.example.authservice.model.Address;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AddressRepository extends ReactiveCrudRepository<Address,Long>{
    Flux<Address> findAllByUserProfileId(Long userProfileId);
    Mono<Address> findByIdAndUserProfileId(Long id,Long userProfileId);
    Mono<Address> findByUserProfileIdAndPrimaryAddressTrue(Long userProfileId);
    Mono<Void> deleteAllByUserProfileId(Long userProfileId);
}