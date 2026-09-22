package org.example.authservice.repository;

import org.example.authservice.model.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AddressRepositoryTest extends BaseRepositoryTest {
    @Autowired private AddressRepository addressRepository;
    @Autowired private DatabaseClient databaseClient;

    @BeforeEach
    void clean() {
        StepVerifier.create(databaseClient.sql("DELETE FROM addresses").fetch().rowsUpdated()
                .then(databaseClient.sql("DELETE FROM sessions").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM refresh_tokens").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_profiles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM user_roles").fetch().rowsUpdated())
                .then(databaseClient.sql("DELETE FROM users_credentials").fetch().rowsUpdated())
                .then(insertUser(1L,"address1@test.com"))
                .then(insertUser(2L,"address2@test.com"))
                .then(databaseClient.sql("INSERT INTO user_profiles(id,auth_user_id,customer_segment,status,created_at,updated_at) VALUES(10,1,'STANDARD','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").fetch().rowsUpdated())
                .then(databaseClient.sql("INSERT INTO user_profiles(id,auth_user_id,customer_segment,status,created_at,updated_at) VALUES(20,2,'STANDARD','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)").fetch().rowsUpdated())).expectNextCount(1).verifyComplete();
    }

    @Test
    void findAllByUserProfileId_whenAddressesExist_shouldReturnOnlyMatchingAddresses() {
        Address first=address(10L,"Hyderabad",true),second=address(10L,"Secunderabad",false),other=address(20L,"Warangal",true);
        StepVerifier.create(addressRepository.saveAll(Flux.just(first,second,other)).thenMany(addressRepository.findAllByUserProfileId(10L)).collectList())
                .assertNext(result->{assertThat(result).hasSize(2);assertThat(result).extracting(Address::getUserProfileId).containsOnly(10L);})
                .verifyComplete();
    }

    @Test
    void findAllByUserProfileId_whenNoAddressExists_shouldReturnEmptyFlux() {
        StepVerifier.create(addressRepository.findAllByUserProfileId(99L)).verifyComplete();
    }

    @Test
    void findByIdAndUserProfileId_whenBothMatch_shouldReturnAddress() {
        StepVerifier.create(addressRepository.save(address(10L,"Hyderabad",true)).flatMap(saved->addressRepository.findByIdAndUserProfileId(saved.getId(),10L)))
                .assertNext(found->{assertThat(found.getId()).isNotNull();assertThat(found.getCity()).isEqualTo("Hyderabad");}).verifyComplete();
    }

    @Test
    void findByIdAndUserProfileId_whenProfileDoesNotMatch_shouldReturnEmptyMono() {
        StepVerifier.create(addressRepository.save(address(10L,"Hyderabad",true)).flatMap(saved->addressRepository.findByIdAndUserProfileId(saved.getId(),20L))).verifyComplete();
    }

    @Test
    void findByUserProfileIdAndPrimaryAddressTrue_whenPrimaryExists_shouldReturnPrimaryAddress() {
        StepVerifier.create(addressRepository.saveAll(Flux.just(address(10L,"Hyderabad",false),address(10L,"Secunderabad",true))).then(addressRepository.findByUserProfileIdAndPrimaryAddressTrue(10L)))
                .assertNext(found->{assertThat(found.getPrimaryAddress()).isTrue();assertThat(found.getCity()).isEqualTo("Secunderabad");}).verifyComplete();
    }

    @Test
    void deleteAllByUserProfileId_whenAddressesExist_shouldDeleteOnlyMatchingAddresses() {
        StepVerifier.create(addressRepository.saveAll(Flux.just(address(10L,"Hyderabad",true),address(10L,"Secunderabad",false),address(20L,"Warangal",true)))
                .then(addressRepository.deleteAllByUserProfileId(10L)).thenMany(addressRepository.findAll()).collectList())
                .assertNext(result->{assertThat(result).hasSize(1);assertThat(result.get(0).getUserProfileId()).isEqualTo(20L);}).verifyComplete();
    }

    private Address address(Long profileId,String city,boolean primary) {
        LocalDateTime now=LocalDateTime.now();
        return Address.builder().userProfileId(profileId).addressLineOne("1 Main Road").city(city).state("Telangana").postalCode("500001").country("India").primaryAddress(primary).createdAt(now).updatedAt(now).build();
    }
}